package controllers.session

import controllers.UserRequest
import helpers.syntax._
import models.User.Credentials
import models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait Session {
  private val user_id_key   = "usr_id"
  private val user_salt_key = "usr_salt"

  implicit def retrieve(implicit request: RequestHeader): Option[Credentials] = {
    val cookie = request.cookies
    Credentials(
      cookie.get(user_id_key).map(_.value),
      cookie.get(user_salt_key).map(_.value)
    ).orElse {
      val session = request.session
      Credentials(
        session.get(user_id_key),
        session.get(user_salt_key)
      )
    }
  }

  def transform[A](request: Request[A]): Future[UserRequest[A]] = {
    request.user.map {new UserRequest[A](_, request)}
  }

  implicit class RequestWithUser(request: RequestHeader) {

    def user: Future[Option[User]] = {
      retrieve(request) match {
        case None       => Future.successful(None)
        case Some(cred) => User.auth(cred)
      }
    }
  }

  implicit class ResultWithSession(result: Result) {

    def createSession(rememberMe: Boolean)(implicit user: User): Result = {
      val maxAge = Some(365.days.toStandardSeconds.getSeconds)

      val resultWithSession = result
        .withNewSession
        .withSession(
          user_id_key -> user.id.toString,
          user_salt_key -> user.salt
        )
        .flashing("success" -> s"Logged in")

      if (!user.remember_me) resultWithSession
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