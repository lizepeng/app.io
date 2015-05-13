package controllers.api

import controllers.api.Bandwidth._
import controllers.api.Groups._
import helpers._
import models._
import models.cfs._
import models.json._
import play.api.Play.current
import play.api.http.ContentTypes
import play.api.libs.MimeTypes
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.BodyParsers.parse.Multipart._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import security.{FilePermission => FilePerm, _}

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
object Files
  extends SecuredController(CFS)
  with AppConfig {

  lazy val bandwidth_upload  : Int =
    getBandwidth("upload").getOrElse(1.5 MBps)
  lazy val bandwidth_download: Int =
    getBandwidth("download").getOrElse(2 MBps)
  lazy val bandwidth_stream  : Int =
    getBandwidth("stream").getOrElse(1 MBps)

  def getBandwidth(key: String): Option[Int] =
    config.getBytes(s"bandwidth.$key").map(_.toInt)

  def download(path: Path, inline: Boolean) =
    PermCheck(_.Show).async { implicit req =>
      serveFile(path) { file =>
        val stm = streamWhole(file)
        if (inline) stm
        else stm.withHeaders(
          CONTENT_DISPOSITION ->
            s"""attachment; filename="${Path.encode(file.name)}" """
        )
      }
    }

  def stream(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      serveFile(path) { file =>
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
    PermCheck(_.Index).async { implicit req =>
      (for {
        root <- CFS.root
        curr <- root.dir(path) if FilePerm(curr).rx.?
        page <- curr.list(pager)
      } yield page).map { page =>
        Ok(
          JsArray(
            page.collect {
              case d: Directory => Json.toJson(d)
              case f: File      => f.toJson
            }.toSeq
          )
        ).withHeaders(
            linkHeader(page, routes.Files.index(path, _))
          )
      }.andThen {
        case Failure(e: FilePerm.Denied) => Logger.trace(e.reason)
      }.recover {
        case e: FilePerm.Denied => NotFound
        case e: BaseException   => NotFound
      }
    }

  def touch(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      val flow = new Flow(req.queryString)
      (for {
        temp <- Home.temp
        file <- temp.file(flow.tempFileName())
      } yield file).map { file =>
        if (flow.currentChunkSize == file.size) Ok
        else NoContent
      }.recover {
        case e: Flow.MissingFlowArgument => NotFound
        case e: Directory.ChildNotFound  => NoContent
      }
    }

  def destroy(path: Path) =
    PermCheck(_.Destroy).async { implicit req =>
      (for {
        root <- CFS.root
        file <- root.file(path)
      } yield file).flatMap { f => f.purge()
      }.map { _ => NoContent
      }.recover {
        case e: Directory.ChildNotFound => NotFound
      }
    }

  def create(path: Path) =
    (MaybeUserAction >> AuthCheck).async(CFSBodyParser(path)) { implicit req =>
      val flow = new Flow(req.body.asFormUrlEncoded)

      def tempFiles(temp: Directory) =
        Enumerator[Int](1 to flow.totalChunks: _*) &>
          Enumeratee.map(flow.tempFileName) &>
          Enumeratee.mapM1(temp.file)

      def summarizer = Iteratee.fold((0, 0L))(
        (s, f: File) => (s._1 + 1, s._2 + f.size)
      )

      req.body.file("file") match {
        case Some(file) =>
          (for {
            ____ <- file.ref.rename(flow.tempFileName(), force = true)
            temp <- Home.temp
            stat <- tempFiles(temp) |>>> summarizer
          } yield stat).flatMap { case (cnt, size) =>
            if (flow.isLastChunk(cnt, size)) {
              (for {
                curr <- Home(req.user).flatMap(_.dir(path))
                file <- Home.temp.flatMap { t =>
                  tempFiles(t) &>
                    Enumeratee.mapFlatten[File] { f =>
                      f.read() &> Enumeratee.onIterateeDone(() => f.purge())
                    } |>>> curr.save(flow.fileName)
                }
              } yield file).map {
                _ => Created
              }.recover {
                case e: Directory.ChildExists => Accepted
              }
            }
            else Future.successful(Accepted)
          }
        case None       => Future.successful(NotFound)
      }
    }

  def mkdir(path: Path)(implicit user: User): Future[Directory] =
    Enumerator(path.parts: _*) |>>>
      Iteratee.fold1(CFS.root) { case (p, cn) =>
        (for (c <- p.dir(cn) if FilePerm(p).rx ?) yield c)
          .recoverWith {
          case e: Directory.ChildNotFound =>
            for (c <- p.mkdir(cn) if FilePerm(p).w ?) yield c
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
        LimitTo(bandwidth_stream)

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
    file.read() &> LimitTo(bandwidth_download)
  )

  private def serveFile(path: Path)(block: File => Result)(
    implicit req: UserRequest[_]
  ): Future[Result] = {
    (for {
      root <- CFS.root
      file <- root.file(path)
    } yield file).map {
      block(_)
    }.recover {
      case e: BaseException => NotFound(e.reason)
    }
  }

  private def contentTypeOf(inode: INode): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }

  def CFSBodyParser(
    path: Path,
    onUnauthorized: RequestHeader => Result = AuthCheck.onUnauthorized,
    onPermDenied: RequestHeader => Result = req => NotFound,
    onPathNotFound: RequestHeader => Result = req => NotFound,
    onFilePermDenied: RequestHeader => Result = req => NotFound,
    onBaseException: RequestHeader => Result = req => NotFound
  )(implicit resource: CheckedResource): BodyParser[MultipartFormData[File]] = {

    def saveTo(dir: Directory)(implicit user: User) = {
      handleFilePart {
        case FileInfo(partName, fileName, contentType) =>
          LimitTo(bandwidth_upload) &>> dir.save()
      }
    }

    SecuredBodyParser(_.Create, onUnauthorized, onPermDenied, onBaseException) {
      req => implicit user =>
        (for {
          home <- Home(user)
          temp <- Home.temp(user)
          dest <- home.dir(path) if FilePerm(dest).w ?
        } yield temp).map { case temp =>
          multipartFormData(saveTo(temp)(user))
        }.recover {
          case _: Directory.NotFound |
               _: Directory.ChildNotFound =>
            parse.error(Future.successful(onPathNotFound(req)))
          case _: FilePerm.Denied         =>
            parse.error(Future.successful(onFilePermDenied(req)))
        }
    }(resource)
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

    def isLastChunk(count: Long, size: Long): Boolean =
      count == totalChunks && size == totalSize

    def tempFileName(index: Int = chunkNum) = s"${id}_$index"

    def flowParam[T](key: String, transform: String => T): T = try {
      transform(form.getOrElse(key, Nil).headOption.get)
    } catch {
      case _: Exception => throw Flow.MissingFlowArgument(key)
    }

    override def toString = form.toString()
  }

  object Flow {

    case class MissingFlowArgument(key: String)
      extends BaseException(msg_key("missing.flow.argument"))

  }

}