package controllers.api

import helpers._
import models.Group
import play.api.http.ContentTypes
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json

/**
 * @author zepeng.li@gmail.com
 */
object Groups extends MVController(Group) {

  implicit val group_writes = Json.writes[Group]

  def index(pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      Group.list(pager).map { list =>
        Ok(Json.prettyPrint(Json.toJson(list))).as(ContentTypes.JSON)
      }.recover {
        case e: BaseException => Logger.error(e.reason); NotFound
      }
    }
}