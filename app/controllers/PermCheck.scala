package controllers

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
    _basicPlayApi: BasicPlayApi,
    _users: Users,
    _accessControls: AccessControls
  ): ActionFunction[MaybeUserRequest, UserRequest] = {
    apply(_.Anything, onDenied)(
      CheckedResource(resource),
      _basicPlayApi,
      _users,
      _accessControls
    )
  }

  def apply(
    action: CheckedActions => CheckedAction,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
    = (_, _, _) => Results.NotFound
  )(
    implicit
    resource: CheckedResource,
    _basicPlayApi: BasicPlayApi,
    _users: Users,
    _accessControls: AccessControls
  ): ActionBuilder[UserRequest] =
    MaybeUserAction() andThen
      AuthCheck andThen
      PermissionChecker(action, onDenied, resource)
}

case class PermCheckRequired(
  _users: Users,
  _accessControls: AccessControls
)

trait PermCheckComponents {

  def _permCheckRequired: PermCheckRequired

  implicit def _users: Users =
    _permCheckRequired._users

  implicit def _accessControls: AccessControls =
    _permCheckRequired._accessControls
}