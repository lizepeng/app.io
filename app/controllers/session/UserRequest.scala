package controllers

import models._
import play.api.Play
import play.api.i18n.Lang
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
class UserRequest[A](
  val user: Option[User],
  request: Request[A]
) extends WrappedRequest[A](request) {

  def lang: Lang = {
    Play.maybeApplication.map {implicit app =>
      val maybeLangFromCookie = request.cookies.get(Play.langCookieName).flatMap(
        c => Lang.get(c.value)
      )
      maybeLangFromCookie.getOrElse(Lang.preferred(request.acceptLanguages))
    }.getOrElse {
      request.acceptLanguages.headOption.getOrElse(Lang.defaultLang)
    }
  }

}