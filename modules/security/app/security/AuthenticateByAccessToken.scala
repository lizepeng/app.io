package security

import java.util.UUID

import helpers._
import models._
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.util.{Failure, Try}

/**
 * @author zepeng.li@gmail.com
 */
case class AuthenticateByAccessToken(
  basicPlayApi: BasicPlayApi
)
  extends PAM
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Logging {

  /**
   * authenticate user by user's id & access token
   *
   * @return authenticated user
   *         [[User.NoCredentials]] - if no credentials provided
   *         [[User.AccessTokenNotMatch]]  - if salt is wrong
   */
  override def apply(users: Users): (RequestHeader) => Future[User] = {
    req => (HttpBasicAuth(req).flatMap {
      case (id, token) => toUUID(id).map((_, token))
    } match {
      case None              => Future.failed(User.NoCredentials())
      case Some((id, token)) =>
        for {
          user <- users.find(id)
          tOpt <- users.findAccessToken(id)
        } yield tOpt
          .collect { case t if t == token && t.nonEmpty => user }
          .getOrElse(throw User.AccessTokenNotMatch(id))
    }).andThen {
      case Failure(e: BaseException) => Logger.info(e.reason)
    }
  }

  private def toUUID(str: String) = Try(UUID.fromString(str)).toOption
}