package security

import helpers._
import models._
import play.api.mvc.BodyParsers.parse
import play.api.mvc._
import security.ModulesAccessControl._

import scala.concurrent.Future
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
trait PermissionCheckedBodyParser[A]
  extends AuthenticatedBodyParser[A] {

  def access: Access
  def resource: CheckedModule
  def preCheck: User => Future[Boolean]

  def errorHandler: BodyParserExceptionHandler

  implicit def basicPlayApi: BasicPlayApi
  implicit def _accessControls: AccessControls

  override def invokeParser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = {
    for {
      passCheck <- preCheck(user)
      canAccess <- ModulesAccessControl(user, access, resource).canAccessAsync
      result <- super.invokeParser(req)(user) if passCheck && canAccess
    } yield result
  }.andThen {
    case Failure(e: Throwable) => Logger.error(s"PermissionCheckedBodyParser failed.", e)
  }.recover {
    case _: BaseException => parse.error(Future.successful(errorHandler.onPermissionDenied(req)))
    case _: Throwable     => parse.error(Future.successful(errorHandler.onThrowable(req)))
  }
}