package controllers

import java.net.URLEncoder
import java.util.UUID

import controllers.session.UserAction
import models.cfs._
import play.api.http.ContentTypes
import play.api.libs.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.mvc.BodyParsers.parse.Multipart._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Files extends Controller {

  def download(id: UUID, inline: Boolean = false) = UserAction.async {
    implicit request => serveFile(id) {file =>
      println(file.name)
      Result(
        ResponseHeader(
          OK, Map(
            CONTENT_LENGTH -> s"${file.size}",
            CONTENT_TYPE -> contentTypeOf(file)
          ) ++ (if (inline) Map.empty
          else Map(
            CONTENT_DISPOSITION ->
              s"""attachment; filename="${URLEncoder.encode(file.name, "UTF-8")}" """
          ))
        ),
        CFS.file.read(file)
      )
    }
  }

  def stream(id: UUID) = UserAction.async {
    implicit request => serveFile(id) {file =>
      request.headers.get(RANGE).flatMap {
        case byte_range_spec(strStart, strEnd) =>
          Some(strStart.toInt, Some(strEnd).filter(_.nonEmpty).map(_.toInt))
        case _                                 => None
      }.filter {
        //invalid
        case (start, endOpt) => endOpt.isEmpty || endOpt.get >= start
      }.map {
        case (start, endOpt) => {
          if (start < file.size) {
            val end = endOpt.filter(_ < file.size).getOrElse(file.size - 1)
            Result(
              ResponseHeader(
                PARTIAL_CONTENT,
                Map(
                  ACCEPT_RANGES -> "bytes",
                  CONTENT_TYPE -> contentTypeOf(file),
                  CONTENT_RANGE -> s"bytes $start-$end/${file.size}",
                  CONTENT_LENGTH -> s"${end - start + 1}"
                )
              ),
              CFS.file.read(file, start) &>
                Traversable.take(end - start + 1)
            )
          } else {
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
        }
      }.getOrElse {
        Result(
          ResponseHeader(
            OK,
            Map(
              ACCEPT_RANGES -> "bytes",
              CONTENT_TYPE -> contentTypeOf(file),
              CONTENT_LENGTH -> s"${file.size}"
            )
          ),
          CFS.file.read(file)
        )
      }
    }
  }

  def index() = UserAction.async {
    implicit request =>
      CFS.file.list().map {all =>
        Ok(html.files.index(all))
      }
  }

  def show(id: UUID) = UserAction.async {implicit request =>
    serveFile(id) {file => Ok(html.files.show(file))}
  }

  def remove(id: UUID) = UserAction {implicit request =>
    INode.remove(id)
    Redirect(routes.Files.index).flashing(
      "success" -> s"${id} deleted"
    )
  }

  def upload() = UserAction(multipartFormData(saveToCFS)) {
    implicit request =>
      request.body.file("picture").map {picture =>
        val ref: File = picture.ref
        Redirect(routes.Files.index).flashing(
          "success" -> s"${ref.name} Uploaded"
        )
      }.getOrElse {
        Redirect(routes.Files.index).flashing(
          "error" -> "Missing file"
        )
      }
  }

  private val byte_range_spec = """bytes=(\d+)-(\d*)""".r

  private def serveFile(id: UUID)(
    whenFound: File => Result
  ): Future[Result] = {
    CFS.file.find(id).map {
      case None       => NotFound
      case Some(file) => whenFound(file)
    }
  }

  private def contentTypeOf(inode: INode): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }

  private def saveToCFS[A]: PartHandler[FilePart[File]] = {
    handleFilePart {
      case FileInfo(partName, filename, contentType) =>
        CFS.file.save(File(filename, CFS.root))
    }
  }
}