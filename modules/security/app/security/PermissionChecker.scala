package security

import akka.stream._
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

  def apply(access: Access, preCheck: User => Future[Boolean])(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    _accessControls: AccessControls,
    eh: UserActionExceptionHandler,
    ec: ExecutionContext
  ) = new ActionFilter[UserRequest] {
    override protected def filter[A](
      req: UserRequest[A]
    ): Future[Option[Result]] = {
      (for {
        passCheck <- preCheck(req.user)
        canAccess <- ModulesAccessControl(req.user, access, resource).canAccessAsync
      } yield passCheck && canAccess).map {
        case true  => None
        case false => Some(eh.onPermissionDenied(req))
      }.recover {
        case _: BaseException => Some(eh.onPermissionDenied(req))
        case _: Throwable     => Some(eh.onThrowable(req))
      }
    }
  }

  def Parser(access: Access, preCheck: User => Future[Boolean])(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    _accessControls: AccessControls,
    eh: BodyParserExceptionHandler,
    ec: ExecutionContext,
    mat: Materializer
  ) = new BodyParserFilter[(RequestHeader, User)] {
    override protected def filter[B](
      req: (RequestHeader, User)
    ): Future[Option[BodyParser[B]]] = {
      val (rh, user) = req
      (for {
        passCheck <- preCheck(user)
        canAccess <- ModulesAccessControl(user, access, resource).canAccessAsync
      } yield passCheck && canAccess).map {
        case true  => None
        case false => Some(parse.error(Future.successful(eh.onPermissionDenied(rh))))
      }.recover {
        case _: BaseException => Some(parse.error(Future.successful(eh.onPermissionDenied(rh))))
        case _: Throwable     => Some(parse.error(Future.successful(eh.onThrowable(rh))))
      }
    }
    def defaultContext = ec
    def materializer = mat
  }
}