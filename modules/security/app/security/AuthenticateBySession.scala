package security

import java.util.UUID

import helpers._
import models._
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
case class AuthenticateBySession(
  basicPlayApi: BasicPlayApi
)
  extends PAM
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Logging {

  import Session._

  /**
   * authenticate user by user's salt
   *
   * @return authenticated user
   *         [[User.NoCredentials]] - if no credentials exists in session or cookie
   *         [[User.SaltNotMatch]]  - if salt is wrong
   */
  override def apply(users: Users): (RequestHeader) => Future[User] = {
    req => retrieve(req) match {
      case None             => Future.failed(User.NoCredentials())
      case Some((id, salt)) => users.find(id).map { user =>
        if (user.salt == salt) user
        else throw User.SaltNotMatch(id)
      }
    }
    //No need to log anything here,
    //since unauthenticated user may be allowed to access some pages.
  }

  def retrieve(req: RequestHeader): Option[(UUID, String)] = {
    val cookie = req.cookies
    for (u <- cookie.get(user_id_key).map(_.value).flatMap(toUUID);
      s <- cookie.get(user_salt_key).map(_.value)
    ) yield (u, s)
  } orElse {
    val session = req.session
    for (u <- session.get(user_id_key).flatMap(toUUID);
      s <- session.get(user_salt_key)
    ) yield (u, s)
  }

  private def toUUID(str: String) = Try(UUID.fromString(str)).toOption
}