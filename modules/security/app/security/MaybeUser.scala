package security

import akka.actor.ActorSystem
import helpers._
import models._
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket._
import play.api.mvc._

import scala.concurrent.Future
import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
case class MaybeUser(
  pamBuilder: BasicPlayApi => PAM = AuthenticateBySession
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
)
  extends Authentication
  with BasicPlayComponents
  with DefaultPlayExecutor {

  def pam: PAM = pamBuilder(basicPlayApi)

  case class InternalActionBuilder()
    extends ActionBuilder[UserOptRequest]
    with ActionTransformer[Request, UserOptRequest] {

    def transform[A](req: Request[A]): Future[UserOptRequest[A]] = {
      pam(_users)(req).map(Some(_)).recover {
        case e: BaseException => None
      }.map(UserOptRequest[A](_, req))
    }

    def >>[Q[_]](other: ActionFunction[UserOptRequest, Q]): ActionBuilder[Q] = andThen(other)
  }

  def Action() = new InternalActionBuilder()

  def WebSocket[In, Out](
    handler: RequestHeader => User => HandlerProps,
    onError: => Result
  )(
    implicit
    actorSystem: ActorSystem,
    transformer: MessageFlowTransformer[In, Out] 
  ) = {
    acceptOrResult({
      req:RequestHeader =>
        pam(_users)(req).map {
          user => Right(handler(req)(user))
        }.recover {
          case e: BaseException => Left(onError)
        }
    }.andThen(_.map(_.right.map { props =>
      ActorFlow.actorRef(props)
    })))
  }
}