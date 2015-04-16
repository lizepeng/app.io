package security

import play.api.mvc._

import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
object UserAction
  extends UserAction
  with ActionTransformer[Request, UserRequest]
  with Session

trait UserAction extends ActionBuilder[UserRequest] {
  def >>[Q[_]](other: ActionFunction[UserRequest, Q]): ActionBuilder[Q] = andThen(other)
}