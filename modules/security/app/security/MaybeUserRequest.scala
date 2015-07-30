package security

import models._
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
trait MaybeUserRequest[A] extends Request[A] {

  def maybeUser: Option[User]

  def inner: Request[A]
}

case class UserOptRequest[A](
  maybeUser: Option[User],
  inner: Request[A]
) extends WrappedRequest[A](inner) with MaybeUserRequest[A]

case class UserRequest[A](
  user: User,
  inner: Request[A]
) extends WrappedRequest[A](inner) with MaybeUserRequest[A] {

  override def maybeUser: Option[User] = Some(user)
}