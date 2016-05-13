package security

import akka.stream._
import helpers._
import models._
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
          case Failure(_: BaseException) => Left(eh.onUnauthorized(req.inner))
          case Failure(_: Throwable)     => Left(eh.onThrowable(req.inner))
          case Success(u)                => Right(UserRequest[A](u, req.inner))
        }
      }
    }
  }

  def Parser(
    implicit
    eh: BodyParserExceptionHandler,
    ec: ExecutionContext,
    mat: Materializer
  ) = new BodyParserRefiner[(RequestHeader, Try[User]), (RequestHeader, User)] {
    override protected def refine[B](
      req: (RequestHeader, Try[User])
    ): Future[Either[BodyParser[B], (RequestHeader, User)]] = {
      val (rh, maybeUser) = req
      Future.successful {
        maybeUser match {
          case Failure(_: BaseException) => Left(parse.error(Future.successful(eh.onUnauthorized(rh))))
          case Failure(_: Throwable)     => Left(parse.error(Future.successful(eh.onThrowable(rh))))
          case Success(u)                => Right(rh -> u)
        }
      }
    }
    def defaultContext = ec
    def materializer = mat
  }
}