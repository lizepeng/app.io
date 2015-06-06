package security

import models.Users
import play.api.mvc._

import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
case class MaybeUserAction(
  implicit val userRepo: Users
)
  extends ActionBuilder[MaybeUserRequest]
  with ActionTransformer[Request, MaybeUserRequest]
  with Session {

  def >>[Q[_]](other: ActionFunction[MaybeUserRequest, Q]): ActionBuilder[Q] = andThen(other)
}