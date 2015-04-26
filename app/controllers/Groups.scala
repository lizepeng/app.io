package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.api.{JsonClientErrors, SecuredController}
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
object Groups
  extends SecuredController(Group)
  with ViewMessages {

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
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.groups.index(pager))
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      Group.find(id).map { grp =>
        Ok(html.groups.show(grp))
      }.recover {
        case e: BaseException => NotFound
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