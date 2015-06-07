package security

import helpers.BasicPlayApi
import models._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class PermissionChecker(
  action: CheckedActions => CheckedAction,
  onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result,
  resource: CheckedResource
)(
  implicit
  val _basicPlayApi: BasicPlayApi,
  val _accessControls: AccessControls
)
  extends ActionFilter[UserRequest]
  with PermissionCheck {

  override protected def filter[A](
    req: UserRequest[A]
  ): Future[Option[Result]] = {

    check(req.user).map {
      case Some(true) => None
      case _          => Some(onDenied(resource, action(CheckedActions), req))
    }
  }
}