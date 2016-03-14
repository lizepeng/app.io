package controllers.api_internal

import controllers.{RateLimitChecker, RateLimitConfig}
import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security.ModulesAccessControl._
import security._

/**
 * @author zepeng.li@gmail.com
 */
case class UserActionRequired(
  _users: Users,
  _accessControls: AccessControls,
  _rateLimits: RateLimits
)

trait UserActionComponents[T <: BasicAccessDef] {
  self: T =>

  def userActionRequired: UserActionRequired

  implicit def _users: Users = userActionRequired._users

  implicit def _accessControls: AccessControls = userActionRequired._accessControls

  implicit def _rateLimits: RateLimits = userActionRequired._rateLimits

  implicit def onDenied: (CheckedModule, Access, RequestHeader) => Result = {
    (_, _, _) => Results.NotFound
  }

  def UserAction(specifiers: (T => Access.Pos)*)(
    implicit
    resource: CheckedModule,
    onDenied: (CheckedModule, Access, RequestHeader) => Result,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired,
    rateLimitConfig: RateLimitConfig
  ): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    MaybeUser().Action() andThen
      AuthChecker andThen
      RateLimitChecker() andThen
      PermissionChecker(access)
  }
}

object UserActionComponents {

  implicit def _users(implicit required: UserActionRequired): Users = required._users

  implicit def _accessControls(implicit required: UserActionRequired): AccessControls = required._accessControls

  implicit def _rateLimits(implicit required: UserActionRequired): RateLimits = required._rateLimits
}