package security

import helpers._
import models._
import play.api.mvc._
import security.ModulesAccessControl._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class PermissionChecker(
  access: Access
)(
  implicit
  val resource: CheckedModule,
  val onDenied: (CheckedModule, Access, RequestHeader) => Result,
  val basicPlayApi: BasicPlayApi,
  val _accessControls: AccessControls
)
  extends ActionFilter[UserRequest]
  with BasicPlayComponents
  with DefaultPlayExecutor {

  override protected def filter[A](
    req: UserRequest[A]
  ): Future[Option[Result]] = {
    def onError = Some(onDenied(resource, access, req))

    ModulesAccessControl(req.user, access, resource)
      .canAccessAsync.map {
      case true  => None
      case false => onError
    }.recover {
      case e: Throwable => onError
    }
  }
}