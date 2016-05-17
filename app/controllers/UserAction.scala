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

  import UsersComponents._

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

trait UserActionComponents[T <: BasicAccessDef] {
  self: T with ExceptionHandlers =>

  import UsersComponents._

  def userActionRequired: UserActionRequired

  implicit def _accessControls: AccessControls = userActionRequired._accessControls

  implicit def _groups: Groups = userActionRequired._groups

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
    pamBuilder: BasicPlayApi => PAM = AuthenticateBySession,
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
    bandwidthConfig: BandwidthConfig
  ): Action[MultipartFormData[File]] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))

    val parser = (MaybeUser(pamBuilder).Parser andThen
      AuthChecker.Parser andThen
      PermissionChecker.Parser(access, preCheck)).async {
      case (rh, u) => CFSBodyParser(path, dirPermission).parser(rh)(u)
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