package controllers

import helpers.{BasicPlayApi, BasicPlayComponents}
import models.UserRepo
import play.api.i18n.I18nSupport
import play.api.mvc.Controller
import security.MaybeUserAction
import views.html

/**
 * @author zepeng.li@gmail.com
 */
class ChatCtrl(
  val basicPlayApi: BasicPlayApi
)(
  implicit
  val userRepo: UserRepo
)
  extends Controller
  with BasicPlayComponents
  with I18nSupport {

  def chat = MaybeUserAction().apply { implicit req =>
    Ok(html.chat.start())
  }
}