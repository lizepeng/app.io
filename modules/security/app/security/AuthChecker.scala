package security

import helpers._
import play.api.mvc.BodyParsers.parse
import play.api.mvc._

import scala.concurrent._
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
object AuthChecker {

  def apply()(
    implicit
    eh: UserActionExceptionHandler
  ) = new ActionRefiner[MaybeUserRequest, UserRequest] {
    override protected def refine[A](
      req: MaybeUserRequest[A]
    ): Future[Either[Result, UserRequest[A]]] = {
      Future.successful {
        req.maybeUser match {
          case Failure(_: BaseException) => Left(eh.onUnauthorized(req))
          case Failure(_: Throwable)     => Left(eh.onThrowable(req))
          case Success(u)                => Right(UserRequestImpl[A](u, req.inner))
        }
      }
    }
  }

  def Parser(
    implicit
    _basicPlayApi: BasicPlayApi,
    eh: BodyParserExceptionHandler
  ) = new BodyParserRefiner[MaybeUserRequestHeader, UserRequestHeader]
    with BasicPlayComponents
    with DefaultPlayExecutor {
    override protected def refine[B](
      req: MaybeUserRequestHeader
    ): Future[Either[BodyParser[B], UserRequestHeader]] = {
      Future.successful {
        req.maybeUser match {
          case Failure(_: BaseException) => Left(parse.error(Future.successful(eh.onUnauthorized(req))))
          case Failure(_: Throwable)     => Left(parse.error(Future.successful(eh.onThrowable(req))))
          case Success(u)                => Right(UserRequestHeaderImpl(u, req.inner))
        }
      }
    }
    def basicPlayApi = _basicPlayApi
  }
}