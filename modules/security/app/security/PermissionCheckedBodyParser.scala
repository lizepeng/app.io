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
  def onPermDenied: RequestHeader => Result
  def preCheck: Future[Boolean]

  implicit def basicPlayApi: BasicPlayApi
  implicit def _accessControls: AccessControls

  override def invokeParser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = {
    for {
      passCheck <- preCheck
      canAccess <- ModulesAccessControl(user, access, resource).canAccessAsync
      result <- super.invokeParser(req)(user) if passCheck && canAccess
    } yield result
  }.andThen {
    case Failure(e: BaseException) => Logger.debug(e.reason)
  }.recover {
    case e: ModulesAccessControl.Denied =>
      parse.error(Future.successful(onPermDenied(req)))
    case e: BaseException               =>
      parse.error(Future.successful(onBaseException(req)))
  }
}