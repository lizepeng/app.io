package controllers.api_internal

import controllers.RateLimitConfig
import helpers.ExtEnumeratee._
import helpers._
import models._
import models.cfs.Directory.{ChildExists, ChildNotFound}
import models.cfs._
import play.api.http.ContentTypes
import play.api.i18n._
import play.api.libs.MimeTypes
import play.api.libs.iteratee.{Enumeratee => _, _}
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import play.core.parsers.Multipart._
import protocols.JsonProtocol.JsonMessage
import protocols._
import security.FileSystemAccessControl._
import security.ModulesAccessControl.AccessDefinition
import security._
import services.BandwidthService
import services.BandwidthService._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
class FileSystemCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val bandwidth: BandwidthService,
  val _cfs: CassandraFileSystem
)
  extends Secured(FileSystemCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with ExceptionDefining
  with I18nSupport
  with AppConfigComponents
  with RateLimitConfig
  with Logging {

  lazy val bandwidth_upload  : Int =
    getBandwidth("upload").getOrElse(1.5 MBps)
  lazy val bandwidth_download: Int =
    getBandwidth("download").getOrElse(2 MBps)
  lazy val bandwidth_stream  : Int =
    getBandwidth("stream").getOrElse(1 MBps)

  def getBandwidth(key: String): Option[Int] =
    config.getBytes(s"bandwidth.$key").map(_.toInt)

  def download(path: Path, inline: Boolean) =
    UserAction(_.Show).async { implicit req =>
      withCached(path) { file =>
        val stm = streamWhole(file)
        if (inline) stm
        else stm.withHeaders(
          CONTENT_DISPOSITION ->
            s"""attachment; filename="${Path.encode(file.name)}" """
        )
      }
    }

  def stream(path: Path) =
    UserAction(_.Show).async { implicit req =>
      withCached(path) { file =>
        val size = file.size
        val byte_range_spec = """bytes=(\d+)-(\d*)""".r

        req.headers.get(RANGE).flatMap[(Long, Option[Long])] {
          case byte_range_spec(first, last) =>
            val byteRange: Some[(Long, Option[Long])] = Some(
              first.toLong,
              Some(last).filter(_.nonEmpty).map(_.toLong)
            )
            byteRange
          case _                            => None
        }.filter {
          case (first, lastOpt) => lastOpt.isEmpty || lastOpt.get >= first
        }.map {
          case (first, lastOpt) if first < size  => streamRange(file, first, lastOpt)
          case (first, lastOpt) if first >= size => invalidRange(file)
        }.getOrElse {
          streamWhole(file).withHeaders(ACCEPT_RANGES -> "bytes")
        }
      }
    }

  def index(path: Path, pager: Pager) =
    UserAction(_.Index).async { implicit req =>
      (for {
        curr <- _cfs.dir(path) if curr.rx.?
        page <- curr.list(pager)
      } yield page).map { page =>
        Ok(Json.toJson(page.elements)).withHeaders(
          linkHeader(page, routes.FileSystemCtrl.index(path, _))
        )
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden
        case e: BaseException                  => NotFound
      }
    }

  def touch(path: Path) =
    UserAction(_.Show).async { implicit req =>
      val flow = new Flow(req.queryString)

      def touchTempFile: Future[Result] = {
        (for {
          size <- flow.currentChunkSize
          temp <- _cfs.temp
          name <- flow.tempFileName
          file <- temp.file(name)
        } yield {
            if (size == file.size) Ok
            else NoContent
          }).recover {
          case e: Flow.MissingFlowArgument => NotFound
          case e: ChildNotFound            => NoContent
        }
      }

      (for {
        fPth <- flow.fullPath(path)
        file <- _cfs.file(fPth) if file.r ?
      } yield {
          Logger.trace(s"file: $fPth already exists.")
          Ok
        }).recoverWith {
        case e: Directory.ChildNotFound => touchTempFile
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden
        case e: BaseException                  => NotFound
      }
    }

  def create(path: Path) =
    UserAction(_.Create).async(CFSBodyParser(path)) { implicit req =>
      val flow = new Flow(req.body.asFormUrlEncoded)

      def tempFiles(temp: Directory): Enumerator[File] =
        Enumerator.flatten(
          flow.totalChunks.map { totalChunks =>
            Enumerator[Int](1 to totalChunks: _*) &>
              Enumeratee.map(flow.genTempFileName) &>
              Enumeratee.mapM1(temp.file)
          }
        )

      def summarizer = Iteratee.fold((0, 0L))(
        (stat, f: File) => {
          val (cnt, size) = stat
          (cnt + 1, size + f.size)
        }
      )

      def concatTempFiles(fPth: Path): Future[Result] = {
        Logger.trace(s"concatenating all temp files of $fPth.")
        (for {
          curr <- _cfs.dir(path)
          file <- _cfs.temp.flatMap { t =>
            tempFiles(t) &>
              Enumeratee.mapFlatten[File] { f =>
                  f.read() &> Enumeratee.onIterateeDone(() => f.delete())
                } |>>> curr.save(fPth.filename.get)
          }
        } yield file).map {
          Logger.trace(s"file: $fPth upload completed.")
          saved => Created(Json.toJson(saved)(File.jsonWrites))
        }.recoverWith {
          case e: ChildExists =>
            Logger.warn(s"file: $fPth was created during uploading, clean temp files.")
            _cfs.temp.flatMap { t =>
              tempFiles(t) |>>> Iteratee.foreach(f => f.delete())
            }.map(_ => Ok)
            Future.successful(Ok)
        }
      }

      def checkIfCompleted: Future[Result] = for {
        fPth <- flow.fullPath(path)
        temp <- _cfs.temp
        stat <- tempFiles(temp) |>>> summarizer
        last <- flow.isLastChunk(stat)
        _ret <- if (last) concatTempFiles(fPth) else Future.successful(Created)
      } yield _ret

      req.body.file("file") match {

        case Some(file) =>
          for {
            tmpName <- flow.tempFileName.andThen {
              case Success(fn) => Logger.trace(s"uploading $fn")
            }
            renamed <- file.ref.rename(tmpName).andThen {
              case Success(false) => file.ref.delete()
            }
            _result <- {
              if (renamed) checkIfCompleted
              //should never occur, only in case that the temp file name was taken.
              else Future.successful(NotFound)
            }
          } yield _result

        case None =>
          val e = FileSystemCtrl.MissingFile()
          Logger.warn(e.message)
          Future.successful(NotFound(JsonMessage(e)))
      }
    }

  def destroy(path: Path, clear: Boolean) =
    UserAction(_.Destroy).async { implicit req =>
      (path.filename match {
        case Some(_) => for {
          file <- _cfs.file(path) if file.w.?
          ____ <- file.delete()
        } yield Unit
        case None    => for {
          dir <- _cfs.dir(path) if dir.w.?
          ___ <- if (clear) dir.clear()
          else dir.delete(recursive = true)
        } yield Unit
      }).map {
        _ => NoContent
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden
        case e: Directory.ChildNotFound        => NotFound
      }
    }

  private def invalidRange(file: File): Result = {
    Result(
      ResponseHeader(
        REQUESTED_RANGE_NOT_SATISFIABLE,
        Map(
          CONTENT_RANGE -> s"*/${file.size}",
          CONTENT_TYPE -> contentTypeOf(file)
        )
      ),
      Enumerator.empty
    )
  }

  private def streamRange(
    file: File,
    first: Long,
    lastOpt: Option[Long]
  ): Result = {
    val end = lastOpt.filter(_ < file.size).getOrElse(file.size - 1)
    Result(
      ResponseHeader(
        PARTIAL_CONTENT,
        Map(
          ACCEPT_RANGES -> "bytes",
          CONTENT_TYPE -> contentTypeOf(file),
          CONTENT_RANGE -> s"bytes $first-$end/${file.size}",
          CONTENT_LENGTH -> s"${end - first + 1}"
        )
      ),
      file.read(first) &>
        Enumeratee.take((end - first + 1).toInt) &>
        bandwidth.LimitTo(bandwidth_stream)

    )
  }

  private def streamWhole(file: File): Result = Result(
    ResponseHeader(
      OK,
      Map(
        CONTENT_TYPE -> contentTypeOf(file),
        CONTENT_LENGTH -> s"${file.size}"
      )
    ),
    file.read() &> bandwidth.LimitTo(bandwidth_download)
  )

  private def withCached(path: Path)(block: File => Result)(
    implicit req: UserRequest[_], messages: Messages
  ): Future[Result] = {
    (for {
      file <- _cfs.file(path) if file.r ?
    } yield file).map {
      NotModifiedOrElse(block)(req, messages)
    }.recover {
      case e: FileSystemAccessControl.Denied => Forbidden
      case e: BaseException                  => NotFound
    }
  }

  private def contentTypeOf(inode: INode): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }

  def CFSBodyParser(
    path: Path,
    onUnauthorized: RequestHeader => Result = AuthChecker.onUnauthorized,
    onPermDenied: RequestHeader => Result = req => NotFound,
    onPathNotFound: RequestHeader => Result = req => NotFound,
    onFilePermDenied: RequestHeader => Result = req => NotFound,
    onBaseException: RequestHeader => Result = req => NotFound
  ): BodyParser[MultipartFormData[File]] = {

    def saveTo(dir: Directory)(implicit user: User) = {
      handleFilePart {
        case FileInfo(partName, fileName, contentType) =>
          bandwidth.LimitTo(bandwidth_upload) &>> dir.save()
      }
    }

    SecuredBodyParser(
      AccessDefinition.Create,
      onUnauthorized,
      onPermDenied,
      onBaseException
    ) {
      req => implicit user =>
        (for {
          temp <- _cfs.temp(user)
          dest <- _cfs.dir(path) if dest.w ?
        } yield temp).map { case temp =>
          multipartFormData(saveTo(temp)(user))
        }.recover {
          case _: Directory.NotFound | _: Directory.ChildNotFound =>
            parse.error(Future.successful(onPathNotFound(req)))
          case _: FileSystemAccessControl.Denied                  =>
            parse.error(Future.successful(onFilePermDenied(req)))
        }
    }
  }

  /**
   * Helper for flow.js
   *
   * @param form queryString or data part in multipart/form
   */
  class Flow(form: Map[String, Seq[String]]) {

    def id = flowParam("flowIdentifier", _.toString)

    def fileName = flowParam("flowFilename", _.toString)

    def relativePath = flowParam("flowRelativePath", _.toString)

    def chunkNum = flowParam("flowChunkNumber", _.toInt)

    def chunkSize = flowParam("flowChunkSize", _.toInt)

    def currentChunkSize = flowParam("flowCurrentChunkSize", _.toInt)

    def totalChunks = flowParam("flowTotalChunks", _.toInt)

    def totalSize = flowParam("flowTotalSize", _.toLong)

    def isLastChunk(stat: (Int, Long)): Future[Boolean] = {
      val (count, size) = stat
      for {
        tc <- totalChunks
        ts <- totalSize
      } yield count == tc && size == ts
    }

    def fullPath(path: Path) = fileName.map(filename => path + filename)

    def genTempFileName(index: Int) = s"${id}_$index"

    def tempFileName = chunkNum.map(genTempFileName)

    def flowParam[T](key: String, transform: String => T): Future[T] = {
      for {
        param <- form.getOrElse(key, Nil).headOption
        value <- Try(transform(param)).toOption
      } yield value
    } match {
      case Some(v) => Future.successful(v)
      case None    => Future.failed(Flow.MissingFlowArgument(key))
    }


    override def toString = form.toString()
  }

  object Flow {

    case class MissingFlowArgument(key: String)
      extends BaseException(error_code("missing.flow.argument"))

  }

}

object FileSystemCtrl
  extends Secured(CassandraFileSystem)
  with ExceptionDefining {

  case class MissingFile()
    extends BaseException(error_code("missing.file"))

}