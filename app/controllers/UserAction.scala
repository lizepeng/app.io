package controllers

import helpers._
import models._
import models.cfs.{CassandraFileSystem => CFS, _}
import play.api.mvc._
import security.ModulesAccessControl._
import security._
import services._

import scala.concurrent._
import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
case class UserActionRequired(
  _groups: Groups,
  _accessControls: AccessControls
)

trait UserActionRequiredComponents extends UsersComponents {

  def userActionRequired: UserActionRequired

  implicit def _groups = userActionRequired._groups
  implicit def _accessControls = userActionRequired._accessControls

  implicit def basicPlayApi: BasicPlayApi
}

trait MaybeUserActionComponents {
  self: PAMBuilderComponents
    with ExceptionHandlers =>

  implicit def _users: Users

  def MaybeUserAction()(
    implicit
    basicPlayApi: BasicPlayApi,
    _groups: Groups
  ): ActionBuilder[UserOptRequest] = {
    MaybeUser(pamBuilder).Action() andThen LayoutLoader()
  }
}

trait UserActionComponents[T <: BasicAccessDef] extends ActionComponents {
  self: T
    with CheckedModuleName
    with PAMBuilderComponents
    with UserActionRequiredComponents
    with ExceptionHandlers =>

  def UserAction(specifiers: (T => Access.Pos)*): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    UserAction0(access, EmptyActionFunction[UserRequest]())
  }

  def UserUploadingToCFS(
    specifiers: (T => Access.Pos)*
  )(
    path: User => Path,
    otherParserChecker: BodyParserFunction[UserRequestHeader, UserRequestHeader] = EmptyBodyParserFunction[UserRequestHeader](),
    otherActionChecker: ActionFunction[UserRequest, UserRequest] = EmptyActionFunction[UserRequest](),
    dirPermission: CFS.Permission = CFS.Role.owner.rwx
  )(
    block: UserRequest[MultipartFormData[File]] => Future[Result]
  )(
    implicit
    _cfs: CFS,
    bandwidth: BandwidthService,
    bandwidthConfig: BandwidthConfig
  ): Action[MultipartFormData[File]] = {
    UserAction10[UserRequestHeader, UserRequest, MultipartFormData[File]](
      Access.union(specifiers.map(_ (this).toAccess)),
      otherParserChecker = otherParserChecker,
      otherActionChecker = otherActionChecker,
      parser = req => CFSBodyParser(path, dirPermission).parser(req)(req.user),
      method = block
    )
  }

  def UserAction10[P, Q[_], A](
    access: Access,
    otherParserChecker: BodyParserFunction[UserRequestHeader, P],
    otherActionChecker: ActionFunction[UserRequest, Q],
    parser: P => Future[BodyParser[A]],
    method: Q[A] => Future[Result]
  ): Action[A] = {
    UserAction1(access, otherActionChecker).async(
      UserBodyParser0(access, otherParserChecker).async(parser)
    )(method)
  }

  def UserBodyParser0[P](
    access: Access,
    otherParserChecker: BodyParserFunction[UserRequestHeader, P]
  ): BodyParserBuilder[P] = {
    MaybeUser(pamBuilder).Parser andThen
      AuthChecker.Parser andThen
      PermissionChecker.Parser(access) andThen
      otherParserChecker
  }

  def UserAction0[P[_]](
    access: Access,
    otherActionChecker: ActionFunction[UserRequest, P]
  ): ActionBuilder[P] = {
    MaybeUser(pamBuilder).Action() andThen
      LayoutLoader() andThen
      AuthChecker() andThen
      PermissionChecker(access) andThen
      otherActionChecker
  }

  def UserAction1[P[_]](
    access: Access,
    otherActionChecker: ActionFunction[UserRequest, P]
  ): ActionBuilder[P] = {
    MaybeUser(pamBuilder).Action() andThen
      AuthChecker() andThen
      PermissionChecker(access) andThen
      otherActionChecker
  }
}

object DefaultUserActionExceptionHandler extends UserActionExceptionHandler {

  def onUnauthorized = _ => Results.Redirect(routes.SessionsCtrl.nnew())
  def onPermissionDenied = _ => Results.Redirect(routes.Application.index())
  def onFilePermissionDenied = _ => Results.Redirect(routes.Application.index())
  def onPathNotFound = _ => Results.Redirect(routes.Application.index())
  def onThrowable = _ => Results.Redirect(routes.Application.index())
}

trait ExceptionHandlers {

  implicit lazy val userActionExceptionHandler = DefaultUserActionExceptionHandler
  implicit lazy val bodyParserExceptionHandler = new BodyParserExceptionHandler with DefaultExceptionHandler
}