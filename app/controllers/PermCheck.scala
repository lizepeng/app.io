package controllers

import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object PermCheck {

  implicit def _users(implicit permCheckRequired: PermCheckRequired): Users = _groups._users

  implicit def _accessControls(implicit permCheckRequired: PermCheckRequired): AccessControls = permCheckRequired._accessControls

  implicit def _groups(implicit permCheckRequired: PermCheckRequired): Groups = permCheckRequired._groups

  implicit def _internalGroups(implicit permCheckRequired: PermCheckRequired): InternalGroups = _users._internalGroups

  def apply(
    action: CheckedActions => CheckedAction,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
    = (_, _, _) => Results.NotFound
  )(
    implicit
    resource: CheckedResource,
    basicPlayApi: BasicPlayApi,
    permCheckRequired: PermCheckRequired
  ): ActionBuilder[UserRequest] = {
    MaybeUserAction() andThen
      LayoutPreference() andThen
      AuthCheck andThen
      PermissionChecker(action, onDenied, resource)
  }

}

//case class PermC(
//  action: CheckedActions => CheckedAction,
//  onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
//  = (_, _, _) => Results.NotFound
//)(
//  implicit
//  resource: CheckedResource,
//  basicPlayApi: BasicPlayApi,
//  val permCheckRequired: PermCheckRequired
//)
//  extends PermCheckComponents
//  with InternalGroupsComponents {
//
//  def Action: ActionBuilder[UserRequest] = {
//    MaybeUserAction() andThen
//      AuthCheck andThen
//      PermissionChecker(action, onDenied, resource) andThen
//      LayoutPreference()
//  }
//}

case class PermCheckRequired(
  _groups: Groups,
  _accessControls: AccessControls
)

trait PermCheckComponents {

  def permCheckRequired: PermCheckRequired

  implicit def _users: Users = _groups._users

  implicit def _accessControls: AccessControls = permCheckRequired._accessControls

  implicit def _groups: Groups = permCheckRequired._groups
}