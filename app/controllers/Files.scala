package controllers

import java.util.UUID

import controllers.Bandwidth._
import controllers.api.SecuredController
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
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
object Files
  extends SecuredController(CFS)
  with ViewMessages
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

        req.headers.get(RANGE).flatMap {
          case byte_range_spec(first, last) => Some(first.toLong, Some(last).filter(_.nonEmpty).map(_.toLong))
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
        home <- Home(req.user)
        curr <- home.dir(path) if FilePermissions(curr).rx.?
        list <- curr.list(pager)
      } yield list).map { list =>
        Ok(html.files.index(path, Page(pager, list)))
      }.andThen {
        case Failure(e: FilePermissions.Denied) => Logger.trace(e.reason)
      }.recover {
        case e: FilePermissions.Denied => Forbidden
        case e: BaseException          => NotFound
      }
    }

  def show(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      serveFile(path) { file => Ok(html.files.show(path, file)) }
    }

  def destroy(id: UUID): Action[AnyContent] =
    PermCheck(_.Destroy).async { implicit req =>
      INode.find(id).map {
        case None        => NotFound(msg("not.found", id))
        case Some(inode) => File.purge(id)
          RedirectToPreviousURI
            .getOrElse(
              Redirect(routes.Files.index(Path()))
            ).flashing(
              AlertLevel.Success -> msg("deleted", inode.name)
            )
      }
    }

  def create(path: Path) =
    PermCheck(_.Create)(CheckedModuleName)(CFSBodyParser(path)) {
      implicit req =>
        req.body.file("files").map { files =>
          val ref: File = files.ref
          Redirect(routes.Files.index(Path())).flashing(
            AlertLevel.Success -> msg("uploaded", ref.name)
          )
        }.getOrElse {
          Redirect(routes.Files.index(Path())).flashing(
            AlertLevel.Info -> msg("missing")
          )
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

  def serveFile(path: Path)(block: File => Result)(
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

  private def CFSBodyParser(path: Path) =
    new AuthorizedBodyParser[MultipartFormData[File]](
      AuthCheck.onUnauthorized,
      req => NotFound
    ) {
      override def parser(
        req: RequestHeader,
        user: User
      ): Future[BodyParser[MultipartFormData[File]]] = (for {
        home <- Home(user)
        curr <- home.dir(path)
      } yield curr).map { case curr =>
        multipartFormData(saveTo(curr)(user))
      }
    }

  private def saveTo(dir: Directory)(implicit user: User) = {
    handleFilePart {
      case FileInfo(partName, filename, contentType) =>
        LimitTo(bandwidth_upload) &>> dir.save(filename)
    }
  }
}