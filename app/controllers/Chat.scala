package controllers

import javax.inject.Inject

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import security.MaybeUserAction
import views.html

/**
 * @author zepeng.li@gmail.com
 */
class Chat @Inject()(val messagesApi: MessagesApi)
  extends Controller with I18nSupport {

  def chat = MaybeUserAction { implicit req =>
    Ok(html.chat.start())
  }
}