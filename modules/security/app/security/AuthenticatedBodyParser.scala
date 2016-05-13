package security

import helpers._
import models.User
import play.api.libs.streams.Accumulator
import play.api.mvc.BodyParsers.parse
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait AuthenticatedBodyParser[A]
  extends BodyParser[A]
    with BasicPlayComponents
    with DefaultPlayExecutor
    with Authentication
    with I18nLogging
    with PAMLogging {

  def errorHandler: BodyParserExceptionHandler

  def basicPlayApi: BasicPlayApi

  override def apply(req: RequestHeader) = {
    Accumulator.flatten {
      authenticate(req).flatMap {
        user => invokeParser(req)(user)
      }.recover {
        case e: BaseException => parse.error(Future.successful(errorHandler.onUnauthorized(req)))
        case e: Throwable     => parse.error(Future.successful(errorHandler.onThrowable(req)))
      }.map(_.apply(req))
    }
  }

  def invokeParser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = parser(req)(user)

  def parser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]]
}