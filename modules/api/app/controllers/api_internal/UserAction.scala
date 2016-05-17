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

trait UserActionRequiredComponents {

  def userActionRequired: UserActionRequired

  implicit def _users = userActionRequired._users
  implicit def _accessControls = userActionRequired._accessControls
  implicit def _rateLimits = userActionRequired._rateLimits
}

trait UserActionComponents[T <: BasicAccessDef] {
  self: T with UserActionRequiredComponents with RateLimitConfigComponents with ExceptionHandlers =>

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
      RateLimitChecker.Parser andThen
      PermissionChecker.Parser(access, preCheck)).async {
      req => CFSBodyParser(path, dirPermission).parser(req)(req.user)
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