package controllers.api

import java.util.UUID

import controllers.api.Bandwidth._
import helpers._
import models._
import models.cfs._
import play.api.Play.current
import play.api.http.ContentTypes
import play.api.libs.MimeTypes
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
import play.api.mvc.BodyParsers.parse.Multipart._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import security.{FilePermissions => FilePerm, _}

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

  def index(path: Path, pager: Pager) = TODO

  //    PermCheck(_.Index).async { implicit req =>
  //      (for {
  //        home <- Home(req.user)
  //        curr <- home.dir(path) if FilePermissions(curr).rx.?
  //        list <- curr.list(pager)
  //      } yield list).map { list =>
  //        Ok(html.files.index(path, Page(pager, list)))
  //      }.andThen {
  //        case Failure(e: FilePermissions.Denied) => Logger.trace(e.reason)
  //      }.recover {
  //        case e: FilePermissions.Denied => Forbidden
  //        case e: BaseException          => NotFound
  //      }
  //    }

  def show(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      val flow = new Flow(req.queryString)
      (for {
        temp <- Home.temp
        file <- temp.file(flow.tempFileName())
      } yield file)
        .map { file =>
        if (flow.currentChunkSize == file.size) Ok
        else NoContent
      }.recover {
        case e: Flow.MissingFlowArgument => NotFound
        case e: File.NotFound            => NoContent
      }
    }

  def destroy(id: UUID): Action[AnyContent] = TODO

  //    PermCheck(_.Destroy).async { implicit req =>
  //      INode.find(id).map {
  //        case None        => NotFound(msg("not.found", id))
  //        case Some(inode) => File.purge(id)
  //          RedirectToPreviousURI
  //            .getOrElse(
  //              Redirect(routes.Files.index(Path()))
  //            ).flashing(
  //              AlertLevel.Success -> msg("deleted", inode.name)
  //            )
  //      }
  //    }

  def create(path: Path) =
    PermCheck(_.Create)(CheckedModuleName)(
      new CFSBodyParser(path)
    ) { implicit req =>
      val flow = new Flow(req.body.asFormUrlEncoded)

      def tempFiles(temp: Directory) =
        Enumerator[Int](1 to flow.totalChunks: _*) &>
          Enumeratee.map(flow.tempFileName) &>
          Enumeratee.mapM(temp.file)

      def summarizer = Iteratee.fold((0, 0L))(
        (s, f: File) => (s._1 + 1, s._2 + f.size)
      )

      req.body.file("file").map { file =>
        (for {
        //According to document of ng-flow,
        //I should allow for the same chunk to be uploaded more than once
          ____ <- file.ref.rename(flow.tempFileName(), force = true)
          temp <- Home.temp
          stat <- tempFiles(temp) |>>> summarizer
        } yield stat).map { case (cnt, size) =>
          if (flow.isLastChunk(cnt, size)) {
            for {
              temp <- Home.temp
              home <- Home(req.user)
              curr <- home.dir(path)
              file <- tempFiles(temp) &>
                Enumeratee.mapFlatten[File] { f =>
                  f.read() &> Enumeratee.onIterateeDone(() => f.purge())
                } |>>>
                curr.save(flow.fileName)
            } yield file
          }
        }
        Ok("")
      }.getOrElse {
        NotFound("")
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
      home <- Home(req.user)
      file <- home.file(path)
    } yield file).map {
      block(_)
    }.recover {
      case e: BaseException => NotFound(e.reason)
    }
  }

  private def contentTypeOf(inode: INode): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }

  class CFSBodyParser(
    path: Path,
    onUnauthorized: RequestHeader => Result = AuthCheck.onUnauthorized,
    onException: RequestHeader => Result = req => NotFound,
    onPathNotFound: RequestHeader => Result = req => NotFound,
    onFilePermDenied: RequestHeader => Result = req => NotFound
  ) extends AuthorizedBodyParser[MultipartFormData[File]](onUnauthorized, onException) {

    def parser(req: RequestHeader)(
      implicit user: User
    ): Future[BodyParser[MultipartFormData[File]]] = {
      println("parsing body") //TODO check perm before parsing body
      (for {
        temp <- Home.temp(user)
      } yield temp).map { case temp =>
        multipartFormData(saveTo(temp)(user))
      }.andThen {
        case Failure(e: BaseException) => Logger.trace(e.reason)
      }.recover {
        case _: Directory.NotFound |
             _: Directory.ChildNotFound =>
          parse.error(Future.successful(onPathNotFound(req)))
        case _: FilePerm.Denied         =>
          parse.error(Future.successful(onFilePermDenied(req)))
      }
    }

    private def saveTo(dir: Directory)
        (implicit user: User): BodyParsers.parse.Multipart.PartHandler[FilePart[File]] = {
      handleFilePart {
        case FileInfo(partName, fileName, contentType) =>
          LimitTo(bandwidth_upload) &>> dir.save()
      }
    }
  }

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