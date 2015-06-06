package controllers.api

import models._
import play.api.Configuration
import play.api.i18n.{Langs, MessagesApi}
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
    langs: Langs,
    messagesApi: MessagesApi,
    configuration: Configuration,
    accessControlRepo: AccessControls,
    userRepo: Users,
    rateLimit: RateLimits,
    internalGroupsRepo: InternalGroupsMapping
  ): ActionFunction[MaybeUserRequest, UserRequest] = {
    apply(_.Anything, onDenied)(
      CheckedResource(resource),
      langs,
      messagesApi,
      configuration,
      accessControlRepo,
      userRepo,
      rateLimit,
      internalGroupsRepo
    )
  }

  def apply(
    action: CheckedActions => CheckedAction,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
    = (_, _, _) => Results.NotFound
  )(
    implicit
    resource: CheckedResource,
    langs: Langs,
    messagesApi: MessagesApi,
    configuration: Configuration,
    accessControlRepo: AccessControls,
    userRepo: Users,
    rateLimit: RateLimits,
    internalGroupsRepo: InternalGroupsMapping
  ): ActionBuilder[UserRequest] =
    MaybeUserAction() andThen
      AuthCheck andThen
      RateLimitCheck(resource) andThen
      PermissionChecker(action, onDenied, resource)
}