package controllers.api

import play.api.i18n.{Messages => MSG}
import play.api.libs.json._
import play.api.mvc.Controller
import security.PermissionCheckable

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Messages extends Controller with PermissionCheckable {

  override val moduleName = "messages"

  def index(keys: List[String]) =
    PermCheck(_.Index).async { implicit req =>
      val messages = keys.distinct.map {
        key => Array(key, MSG(key))
      }

      Future.successful(Ok(Json.toJson(messages)))
    }
}