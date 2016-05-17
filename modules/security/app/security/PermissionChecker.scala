package security

import helpers._
import models._
import play.api.mvc.BodyParsers.parse
import play.api.mvc._
import security.ModulesAccessControl._

import scala.concurrent._
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
object PermissionChecker {

  def apply(access: Access, preCheck: User => Future[Boolean])(
    implicit
    resource: CheckedModule,
    _basicPlayApi: BasicPlayApi,
    _accessControls: AccessControls,
    eh: UserActionExceptionHandler
  ) = new ActionFilter[UserRequest]
    with BasicPlayComponents
    with DefaultPlayExecutor
    with I18nLoggingComponents {
    override protected def filter[A](
      req: UserRequest[A]
    ): Future[Option[Result]] = {
      (for {
        passCheck <- preCheck(req.user)
        canAccess <- ModulesAccessControl(req.user, access, resource).canAccessAsync
      } yield passCheck && canAccess).map {
        case true  => None
        case false => Some(eh.onPermissionDenied(req))
      }.andThen {
        case Failure(e: Denied)        => //Logged in canAccessAsync
        case Failure(e: BaseException) => Logger.error(s"Permission preCheck failed, because ${e.reason}", e)
        case Failure(e: Throwable)     => Logger.error(s"Permission preCheck failed.", e)
      }.recover {
        case _: BaseException => Some(eh.onPermissionDenied(req))
        case _: Throwable     => Some(eh.onThrowable(req))
      }
    }
    def basicPlayApi = _basicPlayApi
  }

  def Parser(access: Access, preCheck: User => Future[Boolean])(
    implicit
    resource: CheckedModule,
    _basicPlayApi: BasicPlayApi,
    _accessControls: AccessControls,
    eh: BodyParserExceptionHandler
  ) = new BodyParserFilter[UserRequestHeader]
    with BasicPlayComponents
    with DefaultPlayExecutor
    with I18nLoggingComponents {
    override protected def filter[B](
      req: UserRequestHeader
    ): Future[Option[BodyParser[B]]] = {
      (for {
        passCheck <- preCheck(req.user)
        canAccess <- ModulesAccessControl(req.user, access, resource).canAccessAsync
      } yield passCheck && canAccess).map {
        case true  => None
        case false => Some(parse.error(Future.successful(eh.onPermissionDenied(req))))
      }.andThen {
        case Failure(e: Denied)        => //Logged in canAccessAsync
        case Failure(e: BaseException) => Logger.error(s"Permission preCheck failed, because ${e.reason}", e)
        case Failure(e: Throwable)     => Logger.error(s"Permission preCheck failed.", e)
      }.recover {
        case _: BaseException => Some(parse.error(Future.successful(eh.onPermissionDenied(req))))
        case _: Throwable     => Some(parse.error(Future.successful(eh.onThrowable(req))))
      }
    }
    def basicPlayApi = _basicPlayApi
  }
}