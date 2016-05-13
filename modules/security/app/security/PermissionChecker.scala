package security

import helpers._
import models._
import play.api.mvc._
import security.ModulesAccessControl._

import scala.concurrent.Future
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
case class PermissionChecker(access: Access)(
  implicit
  val resource: CheckedModule,
  val basicPlayApi: BasicPlayApi,
  val _accessControls: AccessControls,
  val errorHandler: UserActionExceptionHandler
) extends ActionFilter[UserRequest]
  with BasicPlayComponents
  with DefaultPlayExecutor
  with I18nLoggingComponents {

  override protected def filter[A](
    req: UserRequest[A]
  ): Future[Option[Result]] = {
    ModulesAccessControl(req.user, access, resource).canAccessAsync.map {
      case true  => None
      case false => Some(errorHandler.onPermissionDenied(req))
    }.andThen {
      case Failure(e: Throwable) => Logger.error(s"PermissionChecker failed.", e)
    }.recover {
      case _: BaseException => Some(errorHandler.onPermissionDenied(req))
      case _: Throwable     => Some(errorHandler.onThrowable(req))
    }
  }
}