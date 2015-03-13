package controllers

import java.util.UUID

import controllers.session.UserAction
import helpers.Bandwidth._
import helpers._
import models.cfs._
import models.{Home, User}
import play.api.http.ContentTypes
import play.api.i18n.{Messages => MSG}
import play.api.libs.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.mvc.BodyParsers.parse.Multipart._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import views._

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object Files extends Controller with Logging {

  def download(path: Path, inline: Boolean) =
    (UserAction >> AuthCheck).async {implicit request =>
      serveFile(path) {file =>
        val stm = streamWhole(file)
        if (inline) stm
        else stm.withHeaders(
          CONTENT_DISPOSITION ->
            s"""attachment; filename="${Path.encode(file.name)}" """
        )
      }
    }

  def stream(path: Path) =
    UserAction.async {implicit request =>
      serveFile(path) {file =>
        val size = file.size
        val byte_range_spec = """bytes=(\d+)-(\d*)""".r

        request.headers.get(RANGE).flatMap {
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
    (UserAction >> AuthCheck).async {implicit request =>
      (for {
        home <- Home(request.user)
        curr <- home.dir(path)
        list <- curr.list(pager)
      } yield {
        Ok(html.files.index(path, Page(pager, list)))
      }).recover {
        case e: BaseException => NotFound
      }
    }

  def show(path: Path) =
    (UserAction >> AuthCheck).async {implicit request =>
      serveFile(path) {file => Ok(html.files.show(path, file))}
    }

  def destroy(id: UUID): Action[AnyContent] =
    (UserAction >> AuthCheck).async {implicit request =>
      INode.find(id).map {
        case None        => NotFound(MSG("file.not.found", id))
        case Some(inode) => File.purge(id)
          RedirectToPreviousURI.getOrElse(Redirect(routes.Files.index(Path())))
            .flashing(
              AlertLevel.Success -> MSG("file.deleted", inode.name)
            )
      }
    }

  def create(path: Path) =
    (UserAction >> AuthCheck)(CFSBodyParser(path)) {
      implicit request =>
        request.body.file("files").map {files =>
          val ref: File = files.ref
          Redirect(routes.Files.index(Path())).flashing(
            AlertLevel.Success -> MSG("file.uploaded", ref.name)
          )
        }.getOrElse {
          Redirect(routes.Files.index(Path())).flashing(
            AlertLevel.Info -> MSG("file.missing")
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

  private def streamRange(file: File, first: Long, lastOpt: Option[Long]): Result = {
    val end: Long = lastOpt.filter(_ < file.size).getOrElse(file.size - 1)
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
        Enumeratee.take((end - first + 1).toInt) &> LimitTo(1 MBps)
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
    file.read() &> LimitTo(2 MBps)
  )

  def serveFile(path: Path)(block: File => Result)(
    implicit request: UserRequest[AnyContent]
  ): Future[Result] = {
    (for {
      home <- Home(request.user)
      file <- home.file(path)
    } yield {
      block(file)
    }).recover {
      case e: BaseException => NotFound(e.reason)
    }
  }

  private def contentTypeOf(inode: INode): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }

  private def CFSBodyParser(path: Path) =
    new BodyParser[MultipartFormData[File]] with session.Session {
      override def apply(request: RequestHeader) = Iteratee.flatten {
        (for {
          user <- request.user
          home <- Home(user)
          curr <- home.dir(path)
        } yield {
          multipartFormData(saveTo(curr)(user))
        }).recover {
          case e: BaseException => parse.error(Future.successful(BadRequest))
        }.map(_.apply(request))
      }
    }

  private def saveTo(dir: Directory)(implicit user: User) = {
    handleFilePart {
      case FileInfo(partName, filename, contentType) =>
        LimitTo(1.5 MBps) &>> dir.save(filename)
    }
  }
}