package security

import helpers._
import models.User
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.BodyParsers.parse
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
abstract class AuthorizedBodyParser[A](
  onUnauthorized: RequestHeader => Result,
  onException: RequestHeader => Result
) extends BodyParser[A] with security.Session with Logging {

  override def apply(req: RequestHeader): Iteratee[Array[Byte], Either[Result, A]] = {
    Iteratee.flatten {
      req.user.flatMap {
        user => parser(req, user)
      }.andThen {
        case Failure(e: BaseException) => Logger.trace(e.reason)
      }.recover {
        case e: User.NoCredentials =>
          parse.error(Future.successful(onUnauthorized(req)))
        case e: User.AuthFailed    =>
          parse.error(Future.successful(onUnauthorized(req)))
        case e: BaseException      =>
          parse.error(Future.successful(onException(req)))
      }.map(_.apply(req))
    }
  }

  def parser(req: RequestHeader, user: User): Future[BodyParser[A]]
}