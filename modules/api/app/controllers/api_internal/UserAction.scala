package controllers.api_internal

import controllers.RateLimitChecker
import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object UserAction {

  import UserActionComponents._

  def apply(
    action: CheckedActions => CheckedAction,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
    = (_, _, _) => Results.NotFound,
    rateLimit: Int = 500,
    rateLimitSpan: Int = 15
  )(
    implicit
    resource: CheckedResource,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired
  ): ActionBuilder[UserRequest] =
    MaybeUser() andThen
      AuthChecker andThen
      RateLimitChecker(rateLimit, rateLimitSpan) andThen
      PermissionChecker(action, onDenied, resource)
}

case class UserActionRequired(
  _users: Users,
  _accessControls: AccessControls,
  _rateLimits: RateLimits
)

trait UserActionComponents {

  def userActionRequired: UserActionRequired

  implicit def _users: Users = userActionRequired._users

  implicit def _accessControls: AccessControls = userActionRequired._accessControls

  implicit def _rateLimits: RateLimits = userActionRequired._rateLimits
}

object UserActionComponents {

  implicit def _users(implicit required: UserActionRequired): Users = required._users

  implicit def _accessControls(implicit required: UserActionRequired): AccessControls = required._accessControls

  implicit def _rateLimits(implicit required: UserActionRequired): RateLimits = required._rateLimits
}