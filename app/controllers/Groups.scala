package controllers

import controllers.session.UserAction
import helpers._
import models.Group
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import security._
import views.html

/**
 * @author zepeng.li@gmail.com
 */
object Groups
  extends Controller
  with Logging with PermCheckable {

  override val module_name: String = "controllers.groups"

  def index(pager: Pager) =
    (UserAction >> PermCheck(Index)).async { implicit req =>
      Group.list(pager).map { list =>
        Ok(html.groups.index(Page(pager, list)))
      }.recover {
        case e: BaseException => NotFound
      }
    }
}