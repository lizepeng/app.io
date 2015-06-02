package security

import helpers._
import models.User
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.BodyParsers.parse
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
trait AuthorizedBodyParser[A]
  extends BodyParser[A] with security.Session with Logging {

  def onUnauthorized: RequestHeader => Result

  def onBaseException: RequestHeader => Result

  override def apply(req: RequestHeader): Iteratee[Array[Byte], Either[Result, A]] = {
    Iteratee.flatten {
      req.user.flatMap {
        user => invokeParser(req)(user)
      }.andThen {
        case Failure(e: BaseException) => Logger.trace(e.reason)
      }.recover {
        case e: User.NoCredentials =>
          parse.error(Future.successful(onUnauthorized(req)))
        case e: User.SaltNotMatch  =>
          parse.error(Future.successful(onUnauthorized(req)))
        case e: BaseException      =>
          parse.error(Future.successful(onBaseException(req)))
      }.map(_.apply(req))
    }
  }

  def invokeParser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = parser(req)(user)

  def parser(req: RequestHeader)(implicit user: User): Future[BodyParser[A]]
}