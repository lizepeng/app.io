package security

import helpers._
import models._
import play.api.mvc.BodyParsers.parse
import play.api.mvc._
import security.ModulesAccessControl._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
object PermissionChecker {

  def apply(access: Access)(
    implicit
    resource: CheckedModule,
    _basicPlayApi: BasicPlayApi,
    _accessControls: AccessControls,
    eh: UserActionExceptionHandler
  ) = new ActionFilter[UserRequest]
    with BasicPlayComponents
    with DefaultPlayExecutor {
    override protected def filter[A](
      req: UserRequest[A]
    ): Future[Option[Result]] = {
      ModulesAccessControl(req.user, access, resource).canAccessAsync.map {
        case true  => None
        case false => Some(eh.onPermissionDenied(req))
      }.recover {
        case _: BaseException => Some(eh.onPermissionDenied(req))
        case _: Throwable     => Some(eh.onThrowable(req))
      }
    }
    def basicPlayApi = _basicPlayApi
  }

  def Parser(access: Access)(
    implicit
    resource: CheckedModule,
    _basicPlayApi: BasicPlayApi,
    _accessControls: AccessControls,
    eh: BodyParserExceptionHandler
  ) = new BodyParserFilter[UserRequestHeader]
    with BasicPlayComponents
    with DefaultPlayExecutor {
    override protected def filter[B](
      req: UserRequestHeader
    ): Future[Option[BodyParser[B]]] = {
      ModulesAccessControl(req.user, access, resource).canAccessAsync.map {
        case true  => None
        case false => Some(parse.error(Future.successful(eh.onPermissionDenied(req))))
      }.recover {
        case _: BaseException => Some(parse.error(Future.successful(eh.onPermissionDenied(req))))
        case _: Throwable     => Some(parse.error(Future.successful(eh.onThrowable(req))))
      }
    }
    def basicPlayApi = _basicPlayApi
  }
}