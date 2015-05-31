package controllers

import play.api.mvc.Controller
import security.MaybeUserAction
import views.html

/**
 * @author zepeng.li@gmail.com
 */
object Chat extends Controller {

  def chat = MaybeUserAction { implicit req =>
    Ok(html.chat.start())
  }
}