package controllers.api_internal

import controllers._
import helpers._
import models._
import models.cfs.{CassandraFileSystem => CFS, _}
import play.api.mvc._
import security.ModulesAccessControl._
import security._
import services._

import scala.concurrent._
import scala.language.{higherKinds, postfixOps}

/**
 * @author zepeng.li@gmail.com
 */
case class UserActionRequired(
  _users: Users,
  _accessControls: AccessControls,
  _rateLimits: RateLimits
)

trait UserActionRequiredComponents {

  def userActionRequired: UserActionRequired

  implicit def _users = userActionRequired._users
  implicit def _accessControls = userActionRequired._accessControls
  implicit def _rateLimits = userActionRequired._rateLimits
}

trait UserActionComponents[T <: BasicAccessDef] extends ActionComponents {
  self: T with UserActionRequiredComponents with RateLimitConfigComponents with ExceptionHandlers =>

  def UserAction(specifiers: (T => Access.Pos)*)(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    pamBuilder: BasicPlayApi => PAM = AuthenticateBySession
  ): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    UserAction0[UserRequest](access, shouldIncrement = true, EmptyActionFunction[UserRequest]())
  }

  def UserUploadingToCFS(
    specifiers: (T => Access.Pos)*
  )(
    path: User => Path,
    dirPermission: CFS.Permission = CFS.Role.owner.rwx
  )(
    block: UserRequest[MultipartFormData[File]] => Future[Result]
  )(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    pamBuilder: BasicPlayApi => PAM = AuthenticateBySession,
    _cfs: CFS,
    bandwidth: BandwidthService,
    bandwidthConfig: BandwidthConfig
  ): Action[MultipartFormData[File]] = {
    UserAction00[UserRequestHeader, UserRequest, MultipartFormData[File]](
      Access.union(specifiers.map(_ (this).toAccess)),
      otherParserChecker = EmptyBodyParserFunction[UserRequestHeader](),
      otherActionChecker = EmptyActionFunction[UserRequest](),
      parser = req => CFSBodyParser(path, dirPermission).parser(req)(req.user),
      method = block
    )
  }

  def UserAction00[P, Q[_], A](
    access: Access,
    otherParserChecker: BodyParserFunction[UserRequestHeader, P],
    otherActionChecker: ActionFunction[UserRequest, Q],
    parser: P => Future[BodyParser[A]],
    method: Q[A] => Future[Result]
  )(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    pamBuilder: BasicPlayApi => PAM
  ): Action[A] = {
    UserAction0(access, shouldIncrement = false, otherActionChecker).async(
      UserBodyParser0(access, otherParserChecker).async(parser)
    )(method)
  }

  def UserBodyParser0[P](
    access: Access,
    otherParserChecker: BodyParserFunction[UserRequestHeader, P]
  )(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    pamBuilder: BasicPlayApi => PAM
  ): BodyParserBuilder[P] = {
    MaybeUser(pamBuilder).Parser andThen
      AuthChecker.Parser andThen
      RateLimitChecker.Parser andThen
      PermissionChecker.Parser(access) andThen
      otherParserChecker
  }

  def UserAction0[P[_]](
    access: Access,
    shouldIncrement: Boolean,
    otherActionChecker: ActionFunction[UserRequest, P]
  )(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    pamBuilder: BasicPlayApi => PAM
  ): ActionBuilder[P] = {
    MaybeUser(pamBuilder).Action() andThen
      AuthChecker() andThen
      RateLimitChecker(shouldIncrement) andThen
      PermissionChecker(access) andThen
      otherActionChecker
  }
}

trait ExceptionHandlers {

  implicit lazy val userActionExceptionHandler = new UserActionExceptionHandler with DefaultExceptionHandler
  implicit lazy val bodyParserExceptionHandler = new BodyParserExceptionHandler with DefaultExceptionHandler
}