package security

import helpers.BaseException
import play.api.mvc._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * @author zepeng.li@gmail.com
 */
trait AuthenticationCheck
  extends ActionRefiner[MaybeUserRequest, UserRequest] {

  def errorHandler: UserActionExceptionHandler

  override protected def refine[A](
    req: MaybeUserRequest[A]
  ): Future[Either[Result, UserRequest[A]]] = {
    Future.successful {
      req.maybeUser match {
        case Failure(_: BaseException) => Left(errorHandler.onUnauthorized(req.inner))
        case Failure(_: Throwable)     => Left(errorHandler.onThrowable(req.inner))
        case Success(u)                => Right(UserRequest[A](u, req.inner))
      }
    }
  }
}

case class AuthChecker(
  implicit val errorHandler: UserActionExceptionHandler
) extends AuthenticationCheck