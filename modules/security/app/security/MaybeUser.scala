package security

import helpers._
import models.{User, Users}
import play.api.mvc.WebSocket._
import play.api.mvc._

import scala.concurrent.Future
import scala.language.higherKinds
import scala.reflect.ClassTag

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

  import play.api.Play.current

  def WebSocket[In, Out](
    handler: RequestHeader => User => HandlerProps,
    onError: => Result
  )(
    implicit
    in: FrameFormatter[In],
    out: FrameFormatter[Out],
    outMessageType: ClassTag[Out]
  ) = play.api.mvc.WebSocket.tryAcceptWithActor[In, Out] { implicit req =>
    pam(_users)(req).map {
      user => Right(handler(req)(user))
    }.recover {
      case e: BaseException => Left(onError)
    }
  }
}