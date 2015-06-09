package controllers

import helpers.{BasicPlayApi, BasicPlayComponents}
import models.Users
import play.api.i18n.I18nSupport
import play.api.mvc.Controller
import security.MaybeUserAction
import views.html

/**
 * @author zepeng.li@gmail.com
 */
class ChatCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
)
  extends Controller
  with BasicPlayComponents
  with I18nSupport {

  def chat = MaybeUserAction().apply { implicit req =>
    Ok(html.chat.start())
  }
}