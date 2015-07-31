package security

import helpers._
import models.Users
import play.api.mvc._

import scala.concurrent.Future
import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
case class MaybeUserAction(
  pamBuilder: BasicPlayApi => PAM = AuthenticateBySession
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
)
  extends ActionBuilder[MaybeUserRequest]
  with ActionTransformer[Request, MaybeUserRequest]
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Authentication {

  def pam: PAM = pamBuilder(basicPlayApi)

  def transform[A](req: Request[A]): Future[MaybeUserRequest[A]] = {
    pam(_users)(req).map(Some(_)).recover {
      case e: BaseException => None
    }.map(UserOptRequest[A](_, req))
  }

  def >>[Q[_]](other: ActionFunction[MaybeUserRequest, Q]): ActionBuilder[Q] = andThen(other)
}