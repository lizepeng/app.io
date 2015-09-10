package security

import helpers._
import models.Users
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
  extends ActionBuilder[UserOptRequest]
  with ActionTransformer[Request, UserOptRequest]
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Authentication {

  def pam: PAM = pamBuilder(basicPlayApi)

  def transform[A](req: Request[A]): Future[UserOptRequest[A]] = {
    pam(_users)(req).map(Some(_)).recover {
      case e: BaseException => None
    }.map(UserOptRequest[A](_, req))
  }

  def >>[Q[_]](other: ActionFunction[UserOptRequest, Q]): ActionBuilder[Q] = andThen(other)
}