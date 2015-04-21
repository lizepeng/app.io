package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.api.{JsonClientErrors, MVController}
import helpers._
import models.Group
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Groups extends MVController(Group) {

  implicit val group_writes = Json.writes[Group]

  val mapping_name = "name" -> nonEmptyText(2, 255)

  val GroupFM = Form[Group](
    mapping(
      "id" -> default(of[UUID], UUIDs.timeBased()),
      mapping_name,
      "description" -> optional(text(1, 255)),
      "isInternal" -> default(boolean, false)
    )(Group.apply)(Group.unapply)
  )

  def index(pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      Future.successful {
        Ok(html.groups.index(pager, GroupFM))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create(pager: Pager) = {
    PermCheck(_.Index).async { implicit req =>
      val bound: Form[Group] = GroupFM.bindFromRequest()
      bound.fold(
        failure => Future.successful {
          BadRequest {
            html.groups.index(pager, bound)
          }
        },
        success => success.save.map { _ =>
          Redirect(routes.Groups.index(pager))
            .flashing(
              AlertLevel.Success -> msg("created")
            )
        }
      )
    }
  }

  def save(id: UUID) =
    PermCheck(_.Save).async { implicit req =>
      val bound = GroupFM.bindFromRequest()
      bound.fold(
        failure => Future.successful(BadRequest(bound.errorsAsJson)),
        success => (
          for {
            ___ <- Group.find(id)
            grp <- success.save
          } yield {
            Ok
          }).recover {
          case e: Group.NotFound => NotFound
        }
      )
    }

  def destroy(id: UUID) =
    PermCheck(_.Destroy).async { implicit req =>
      Group.remove(id).map { _ =>
        RedirectToPreviousURI
          .getOrElse(
            Redirect(routes.Groups.index())
          ).flashing(
            AlertLevel.Success -> msg("entry.deleted")
          )
      }
    }

  def checkName =
    PermCheck(_.Show).async(parse.json) { implicit req =>
      Future.successful {
        req.body.validate(Group.reads_name).fold(
          failure => UnprocessableEntity(JsonClientErrors(failure)),
          success => Ok
        )
      }
    }
}