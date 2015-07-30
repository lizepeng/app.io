package controllers.api_internal

import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object PermCheck {

  def apply(
    resource: String,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
  )(
    implicit
    basicPlayApi: BasicPlayApi,
    _users: Users,
    _accessControls: AccessControls,
    _rateLimits: RateLimits
  ): ActionFunction[MaybeUserRequest, UserRequest] = {
    apply(_.Anything, onDenied)(
      CheckedResource(resource),
      basicPlayApi,
      _users,
      _accessControls,
      _rateLimits
    )
  }

  def apply(
    action: CheckedActions => CheckedAction,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
    = (_, _, _) => Results.NotFound
  )(
    implicit
    resource: CheckedResource,
    basicPlayApi: BasicPlayApi,
    _users: Users,
    _accessControls: AccessControls,
    _rateLimits: RateLimits
  ): ActionBuilder[UserRequest] =
    MaybeUserAction() andThen
      AuthCheck andThen
      RateLimitCheck() andThen
      PermissionChecker(action, onDenied, resource)
}

case class PermCheckRequired(
  _users: Users,
  _accessControls: AccessControls,
  _rateLimits: RateLimits
)

trait PermCheckComponents {

  def permCheckRequired: PermCheckRequired

  implicit def _users: Users =
    permCheckRequired._users

  implicit def _accessControls: AccessControls =
    permCheckRequired._accessControls

  implicit def _rateLimits: RateLimits =
    permCheckRequired._rateLimits
}