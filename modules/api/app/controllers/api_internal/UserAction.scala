package controllers.api_internal

import akka.stream._
import controllers._
import helpers._
import models._
import models.cfs.{CassandraFileSystem => CFS, _}
import play.api.mvc._
import security.ModulesAccessControl._
import security._
import services._

import scala.concurrent._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class UserActionRequired(
  _users: Users,
  _accessControls: AccessControls,
  _rateLimits: RateLimits
)

trait UserActionComponents[T <: BasicAccessDef] {
  self: T with RateLimitConfigComponents with ExceptionHandlers =>

  def userActionRequired: UserActionRequired

  implicit def _users: Users = userActionRequired._users
  implicit def _accessControls: AccessControls = userActionRequired._accessControls
  implicit def _rateLimits: RateLimits = userActionRequired._rateLimits

  def UserAction(specifiers: (T => Access.Pos)*)(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired,
    executionContext: ExecutionContext
  ): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    MaybeUser().Action() andThen
      AuthChecker() andThen
      RateLimitChecker() andThen
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
      RateLimitChecker.Parser andThen
      PermissionChecker.Parser(access, preCheck)).async {
      case (rh, u) => CFSBodyParser(path, dirPermission).parser(rh)(u)
    }

    (MaybeUser(pamBuilder).Action() andThen
      AuthChecker() andThen
      RateLimitChecker(shouldIncrement = false) andThen
      PermissionChecker(access, preCheck)).async(parser)(block)
  }
}

trait ExceptionHandlers {

  implicit lazy val userActionExceptionHandler = new UserActionExceptionHandler with DefaultExceptionHandler
  implicit lazy val bodyParserExceptionHandler = new BodyParserExceptionHandler with DefaultExceptionHandler
}