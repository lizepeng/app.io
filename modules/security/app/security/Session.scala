package security

import java.util.UUID

import helpers._
import helpers.syntax._
import models.User._
import models._
import play.api.mvc._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait Session {
  self: DefaultPlayExecutor =>

  def _basicPlayApi: BasicPlayApi

  def _users: Users

  private val user_id_key   = "usr_id"
  private val user_salt_key = "usr_salt"

  case class UserOptRequest[A](
    maybeUser: Option[User],
    inner: Request[A]
  ) extends WrappedRequest[A](inner) with MaybeUserRequest[A]

  def transform[A](req: Request[A]): Future[MaybeUserRequest[A]] = {
    req.user.map(Some(_)).recover {
      case e: BaseException => None
    }.map {new UserOptRequest[A](_, req)}
  }

  implicit def retrieve(implicit req: RequestHeader): Option[Credentials] = {
    val cookie = req.cookies
    for (u <- cookie.get(user_id_key).map(_.value).flatMap(toUUID);
      s <- cookie.get(user_salt_key).map(_.value)
    ) yield Credentials(u, s)
  } orElse {
    val session = req.session
    for (u <- session.get(user_id_key).flatMap(toUUID);
      s <- session.get(user_salt_key)
    ) yield Credentials(u, s)
  }

  private def toUUID(str: String) = Try(UUID.fromString(str)).toOption

  implicit class RequestWithUser(req: RequestHeader) {

    /**
     * authorize user by user's salt
     *
     * @return authorized user
     *         [[User.NoCredentials]] - if no credentials exists
     *         [[User.SaltNotMatch]]  - if salt is not right
     */
    def user: Future[User] = retrieve(req) match {
      case None       => Future.failed(User.NoCredentials())
      case Some(cred) => _users.auth(cred)
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