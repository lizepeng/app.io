package security

import akka.actor.ActorSystem
import helpers._
import models._
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket._
import play.api.mvc._

import scala.concurrent._
import scala.language.higherKinds
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
case class MaybeUser(
  pamBuilder: BasicPlayApi => PAM = AuthenticateBySession
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
) extends Authentication
  with BasicPlayComponents
  with DefaultPlayExecutor
  with I18nLoggingComponents
  with PAMLogging {

  val pam: PAM = pamBuilder(basicPlayApi)

  case class InternalActionBuilder()
    extends ActionBuilder[UserOptRequest]
      with ActionTransformer[Request, UserOptRequest] {

    def transform[A](req: Request[A]): Future[UserOptRequest[A]] = {
      authenticate(req).map {
        user => Success(user)
      }.recover {
        case e: Throwable => Failure(e)
      }.map(UserOptRequest[A](_, req))
    }

    def >>[Q[_]](other: ActionFunction[UserOptRequest, Q]): ActionBuilder[Q] = andThen(other)
  }

  def Action() = new InternalActionBuilder()

  def WebSocket[In, Out](
    handler: RequestHeader => User => HandlerProps
  )(
    implicit
    actorSystem: ActorSystem,
    transformer: MessageFlowTransformer[In, Out],
    errorHandler: UserActionExceptionHandler
  ) = acceptOrResult(
    {
      req: RequestHeader =>
        authenticate(req).map {
          user => Right(handler(req)(user))
        }.recover {
          case _: BaseException => Left(errorHandler.onUnauthorized(req))
          case _: Throwable     => Left(errorHandler.onThrowable(req))
        }
    }.andThen(
      _.map(
        _.right.map { props =>
          ActorFlow.actorRef(props)
        }
      )
    )
  )
}