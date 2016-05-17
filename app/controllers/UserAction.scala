package controllers

import akka.stream._
import helpers._
import models._
import models.cfs.{CassandraFileSystem => CFS, _}
import play.api.mvc._
import security.ModulesAccessControl._
import security._
import services._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
trait MaybeUserActionComponents {
  self: ExceptionHandlers =>

  implicit def _users: Users

  def MaybeUserAction()(
    implicit
    basicPlayApi: BasicPlayApi,
    _groups: Groups
  ): ActionBuilder[UserOptRequest] = {
    MaybeUser().Action() andThen
      LayoutLoader()
  }
}

case class UserActionRequired(
  _groups: Groups,
  _accessControls: AccessControls
)

trait UserActionRequiredComponents extends UsersComponents {

  def userActionRequired: UserActionRequired

  implicit def _groups = userActionRequired._groups
  implicit def _accessControls = userActionRequired._accessControls
}

trait UserActionComponents[T <: BasicAccessDef] {
  self: T with UserActionRequiredComponents with ExceptionHandlers =>

  def UserAction(specifiers: (T => Access.Pos)*)(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired,
    executionContext: ExecutionContext
  ): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    MaybeUser().Action() andThen
      LayoutLoader() andThen
      AuthChecker() andThen
      PermissionChecker(access, _ => Future.successful(true))
  }

  def UserUploadingToCFS(
    specifiers: (T => Access.Pos)*
  )(
    path: User => Path,
    preCheck: User => Future[Boolean] = user => Future.successful(true),
    dirPermission: CFS.Permission = CFS.Role.owner.rwx
  )(
    block: UserRequest[MultipartFormData[File]] => Future[Result]
  )(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired,
    executionContext: ExecutionContext,
    materializer: Materializer,
    _cfs: CFS,
    bandwidth: BandwidthService,
    bandwidthConfig: BandwidthConfig,
    pamBuilder: BasicPlayApi => PAM = AuthenticateBySession
  ): Action[MultipartFormData[File]] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))

    val parser = (MaybeUser(pamBuilder).Parser andThen
      AuthChecker.Parser andThen
      PermissionChecker.Parser(access, preCheck)).async {
      req => CFSBodyParser(path, dirPermission).parser(req)(req.user)
    }

    (MaybeUser(pamBuilder).Action() andThen
      AuthChecker() andThen
      PermissionChecker(access, preCheck)).async(parser)(block)
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