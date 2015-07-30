package security

import java.util.UUID

import helpers._
import models.User.Credentials
import models._
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.util.{Failure, Try}

/**
 * @author zepeng.li@gmail.com
 */
case class AuthenticateBySession(
  implicit
  val basicPlayApi: BasicPlayApi
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
    req => (retrieve(req) match {
      case None       => Future.failed(User.NoCredentials())
      case Some(cred) => users.auth(cred)
    }).andThen {
      case Failure(e: BaseException) => Logger.info(e.reason)
    }
  }

  def retrieve(req: RequestHeader): Option[Credentials] = {
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
}