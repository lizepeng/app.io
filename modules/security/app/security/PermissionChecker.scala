package security

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class PermissionChecker(
  action: CheckedActions => CheckedAction,
  onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result,
  resource: CheckedResource
) extends ActionFilter[UserRequest] with PermissionCheck {

  override protected def filter[A](
    req: UserRequest[A]
  ): Future[Option[Result]] = {

    check(req.user).map {
      case Some(true) => None
      case _          => Some(onDenied(resource, action(CheckedActions), req))
    }
  }
}