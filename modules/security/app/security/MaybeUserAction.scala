package security

import helpers._
import models.Users
import play.api.mvc._

import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
case class MaybeUserAction(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
)
  extends ActionBuilder[MaybeUserRequest]
  with ActionTransformer[Request, MaybeUserRequest]
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Session {

  def >>[Q[_]](other: ActionFunction[MaybeUserRequest, Q]): ActionBuilder[Q] = andThen(other)
}