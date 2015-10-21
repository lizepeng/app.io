package controllers

import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security.ModulesAccessControl._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object UserAction {

  import UserActionRequired._
  import UsersComponents._

  def apply(specifiers: (AccessDefinition => Access)*)(
    implicit
    resource: CheckedModule,
    onDenied: (CheckedModule, Access, RequestHeader) => Result,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired
  ): ActionBuilder[UserRequest] = apply(
    AccessDefinition.union(specifiers.map(_(AccessDefinition)): _*)
  )

  def apply(access: Access)(
    implicit
    resource: CheckedModule,
    onDenied: (CheckedModule, Access, RequestHeader) => Result,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired
  ): ActionBuilder[UserRequest] = {
    MaybeUser().Action() andThen
      LayoutLoader() andThen
      AuthChecker andThen
      PermissionChecker(access)
  }

}

object MaybeUserAction {

  import UsersComponents._

  def apply()(
    implicit
    basicPlayApi: BasicPlayApi,
    _groups: Groups
  ): ActionBuilder[UserOptRequest] = {
    MaybeUser().Action() andThen LayoutLoader()
  }
}

case class UserActionRequired(
  _groups: Groups,
  _accessControls: AccessControls
)

object UserActionRequired {

  implicit def _accessControls(implicit required: UserActionRequired): AccessControls = required._accessControls

  implicit def _groups(implicit required: UserActionRequired): Groups = required._groups

}

trait UserActionComponents {

  def userActionRequired: UserActionRequired

  implicit def _accessControls: AccessControls = userActionRequired._accessControls

  implicit def _groups: Groups = userActionRequired._groups

  implicit def onDenied: (CheckedModule, Access, RequestHeader) => Result = {
    (_, _, _) => Results.NotFound
  }
}