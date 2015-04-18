package controllers.api

import helpers._
import models.Group
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object Groups extends MVController(Group) {

  implicit val group_writes = Json.writes[Group]

  def index(pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      Group.list(pager).map { list =>
        Ok(Json.toJson(list))
      }.recover {
        case e: BaseException => NotFound
      }
    }
}