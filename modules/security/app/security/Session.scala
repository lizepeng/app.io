package security

import helpers.syntax._
import models._
import play.api.mvc._

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object Session {

  val user_id_key   = "usr_id"
  val user_salt_key = "usr_salt"
}

trait Session {

  implicit class ResultWithSession(result: Result) {

    import Session._

    def createSession(rememberMe: Boolean)(implicit user: User): Result = {
      val maxAge = Some(365.days.toStandardSeconds.getSeconds)

      val resultWithSession = result
        .withNewSession
        .withSession(
          user_id_key -> user.id.toString,
          user_salt_key -> user.salt
        )

      if (!rememberMe) resultWithSession
      else {
        resultWithSession.withCookies(
          Cookie(user_id_key, user.id.toString, maxAge = maxAge),
          Cookie(user_salt_key, user.salt, maxAge = maxAge)
        )
      }
    }

    def destroySession: Result = {
      result.withNewSession
        .discardingCookies(
          DiscardingCookie(user_id_key),
          DiscardingCookie(user_salt_key)
        )
    }
  }

}