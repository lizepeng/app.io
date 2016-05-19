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
) extends PAM
  with BasicPlayComponents
  with DefaultPlayExecutor
  with I18nLoggingComponents {

  def basicName = "AuthenticateBySession"

  /**
   * authenticate user by user's salt
   *
   * @return authenticated user
   *         [[User.NoCredentials]] - if no credentials exists in session or cookie
   *         [[User.SessionIdNotMatch]]  - if session id is wrong
   */
  override def apply(users: Users): (RequestHeader) => Future[User] = {
    req => retrieve(req) match {
      case None                => Future.failed(User.NoCredentials())
      case Some((id, sess_id)) => users.find(id).map { user =>
        if (user.session_id.contains(sess_id)) user
        else throw User.SessionIdNotMatch(id)
      }
    }
    //No need to log anything here,
    //since unauthenticated user may be allowed to access some pages.
  }

  def retrieve(req: RequestHeader): Option[(UUID, String)] = {
    val session = req.session
    for (u <- session.get(Session.USER_ID_KEY).flatMap(toUUID);
      s <- session.get(Session.SESS_ID_KEY)
    ) yield (u, s)
  }

  private def toUUID(str: String) = Try(UUID.fromString(str)).toOption
}

trait AuthenticateBySessionComponents extends PAMBuilderComponents {

  implicit def pamBuilder: BasicPlayApi => PAM = AuthenticateBySession
}