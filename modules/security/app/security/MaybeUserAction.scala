package security

import play.api.mvc._

import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
object MaybeUserAction
  extends ActionBuilder[MaybeUserRequest]
  with ActionTransformer[Request, MaybeUserRequest]
  with Session {

  def >>[Q[_]](other: ActionFunction[MaybeUserRequest, Q]): ActionBuilder[Q] = andThen(other)
}