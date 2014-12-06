package controllers

import java.util.UUID

import controllers.session.UserAction
import models.cfs._
import play.api.http.ContentTypes
import play.api.libs.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.BodyParsers.parse.Multipart._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import views._

/**
 * @author zepeng.li@gmail.com
 */
object Files extends Controller {

  def download(id: UUID, inline: Boolean = false) =
    UserAction.async {implicit request =>
      INode.find(id).map {
        case None        => NotFound
        case Some(inode) => {
          val name = inode.name
          Result(
            ResponseHeader(
              OK, Map(
                CONTENT_LENGTH -> inode.size.toString,
                CONTENT_TYPE -> MimeTypes.forFileName(name).getOrElse(ContentTypes.BINARY)
              ) ++ (
                if (inline) Map.empty
                else Map(CONTENT_DISPOSITION -> s"""attachment; filename="$name" """))
            ),
            CFS.file.read(id)
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

  private def saveToCFS[A]: PartHandler[FilePart[File]] = {
    handleFilePart {
      case FileInfo(partName, filename, contentType) =>
        CFS.file.save(File(filename, CFS.root))
    }
  }
}