package controllers.api_internal

import controllers._
import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security.ModulesAccessControl._
import security._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
case class UserActionRequired(
  _users: Users,
  _accessControls: AccessControls,
  _rateLimits: RateLimits
)

trait UserActionComponents[T <: BasicAccessDef] {
  self: T with ExceptionHandlers =>

  def userActionRequired: UserActionRequired

  implicit def _users: Users = userActionRequired._users
  implicit def _accessControls: AccessControls = userActionRequired._accessControls
  implicit def _rateLimits: RateLimits = userActionRequired._rateLimits


  def UserAction(specifiers: (T => Access.Pos)*)(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired,
    rateLimitConfig: RateLimitConfig,
    executionContext: ExecutionContext
  ): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    MaybeUser().Action() andThen
      AuthChecker() andThen
      RateLimitChecker() andThen
      PermissionChecker(access, _ => Future.successful(true))
  }
}

trait ExceptionHandlers {

  implicit lazy val userActionExceptionHandler = new UserActionExceptionHandler with DefaultExceptionHandler
  implicit lazy val bodyParserExceptionHandler = new BodyParserExceptionHandler with DefaultExceptionHandler
}