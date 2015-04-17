package security

import models._
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
case class UserRequest[A](
  user: User,
  inner: Request[A]
) extends WrappedRequest[A](inner) with MaybeUserRequest[A] {

  override def maybeUser: Option[User] = Some(user)
}