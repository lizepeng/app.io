

package controllers

import java.util.UUID

import controllers.session.UserAction
import helpers._
import models.Group
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import security._
import views.html

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Groups extends MVController(Group) {

  implicit val group_writes = Json.writes[Group]

  val mapping_name = "name" -> nonEmptyText(2, 255)

  val GroupFM = Form[Group](
    mapping(
      "id" -> of[UUID],
      mapping_name,
      "description" -> optional(text(1, 255))
    )(Group.apply)(Group.unapply)
  )

  def index(pager: Pager) =
    (UserAction >> PermCheck(Index)).async { implicit req =>
      Group.list(pager).map { list =>
        render {
          case Accepts.Html() =>
            Ok(html.groups.index(Page(pager, list)))
          case Accepts.Json() =>
            Ok(Json.toJson(list))
        }
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def save =
    (UserAction >> PermCheck(Save)).async { implicit req =>
      val bound = GroupFM.bindFromRequest()
      bound.fold(
        failure => Future.successful(BadRequest(bound.errorsAsJson)),
        success => success.save.map(_ => Ok)
      )
    }

  def checkName =
    (UserAction >> PermCheck(Show)) { implicit req =>
      val bound = Form(single(mapping_name)).bindFromRequest()
      bound.fold(
        failure => BadRequest(bound.errorsAsJson),
        success => Ok
      )
    }
}