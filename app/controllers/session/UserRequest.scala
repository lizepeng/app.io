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
  req: Request[A]
) extends WrappedRequest[A](req) {

  def lang1: Lang = {
    Play.maybeApplication.map {implicit app =>
      val maybeLangFromCookie = req.cookies.get(Play.langCookieName).flatMap(
        c => Lang.get(c.value)
      )
      maybeLangFromCookie.getOrElse(Lang.preferred(req.acceptLanguages))
    }.getOrElse {
      req.acceptLanguages.headOption.getOrElse(Lang.defaultLang)
    }
  }

}