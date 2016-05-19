package security

import helpers._
import models._
import play.api.mvc._

import scala.concurrent._
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait Authentication extends PAMLogging {
  self: DefaultPlayExecutor =>

  def _users: Users

  def pam: PAM

  def authenticate(req: RequestHeader) =
    pam(_users)(req).andThen {
      loggingPAMExceptions { reason =>
        s"PAM[${pam.basicName}] auth failed, because $reason"
      }
    }
}

trait PAM extends (Users => RequestHeader => Future[User])
  with CanonicalNamed {
  self: I18nLogging =>

  def thenTry(that: PAM)(
    implicit ec: ExecutionContext
  ): PAM = new ThenTryPAM(self, that)

  def >>(that: PAM)(implicit ec: ExecutionContext) = thenTry(that)
}

class ThenTryPAM(first: PAM, second: PAM)(
  implicit
  val loggingMessages: LoggingMessages,
  val ec: ExecutionContext
) extends PAM
  with CanonicalNamed
  with I18nLogging
  with PAMLogging {

  def basicName = second.basicName

  def apply(v1: Users): (RequestHeader) => Future[User] = {
    req => first.apply(v1)(req).andThen {
      loggingPAMExceptions { reason =>
        s"PAM[${first.basicName}] auth failed, because $reason, then try PAM[${second.basicName}]"
      }
    }.recoverWith {
      case _: Throwable => second.apply(v1)(req)
    }
  }
}

trait PAMLogging extends I18nLogging {

  def loggingPAMExceptions(message: String => String): PartialFunction[Try[User], Unit] = {
    case Failure(e: User.NoCredentials)       => Logger.debug(s"${message(e.reason)}")
    case Failure(e: User.SessionIdNotMatch)   => Logger.debug(s"${message(e.reason)}")
    case Failure(e: User.AccessTokenNotMatch) => Logger.debug(s"${message(e.reason)}")
    case Failure(e: User.NotFound)            => Logger.error(s"${message(e.reason)}")
    case Failure(e: BaseException)            => Logger.debug(s"${message(e.reason)}", e)
    case Failure(e: Throwable)                => Logger.error(s"${message(e.getMessage)}", e)
  }
}

trait PAMBuilderComponents {

  implicit def pamBuilder: BasicPlayApi => PAM
}