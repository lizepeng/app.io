package security

import models._
import play.api.mvc._

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait MaybeUserRequest[A] extends Request[A] {

  def maybeUser: Try[User]

  def inner: Request[A]
}

case class UserOptRequest[A](
  maybeUser: Try[User],
  inner: Request[A]
) extends WrappedRequest[A](inner) with MaybeUserRequest[A]

case class UserRequest[A](
  user: User,
  inner: Request[A]
) extends WrappedRequest[A](inner) with MaybeUserRequest[A] {

  override def maybeUser: Try[User] = Success(user)
}