package security

import models._
import play.api.mvc._

import scala.util._

/**
 * A HTTP request that may be made by a user.
 */
trait MaybeUserRequest[A] extends Request[A] {

  def maybeUser: Try[User]

  def inner: Request[A]
}

/**
 * Default implementation of [[MaybeUserRequest]].
 */
case class UserOptRequest[A](
  maybeUser: Try[User],
  inner: Request[A]
) extends WrappedRequest[A](inner) with MaybeUserRequest[A]

/**
 * A HTTP request that made by a user.
 */
trait UserRequest[A] extends MaybeUserRequest[A] {

  def user: User

  def inner: Request[A]

  def maybeUser: Try[User] = Success(user)
}

/**
 * Default implementation of [[UserRequest]].
 */
private[security] case class UserRequestImpl[A](
  user: User,
  inner: Request[A]
) extends WrappedRequest[A](inner) with UserRequest[A]

/**
 * A HTTP request header that may be made by a user.
 */
trait MaybeUserRequestHeader extends RequestHeader {

  def maybeUser: Try[User]

  def inner: RequestHeader
}

/**
 * Default implementation of [[MaybeUserRequestHeader]].
 */
case class UserOptRequestHeader(
  maybeUser: Try[User],
  inner: RequestHeader
) extends WrappedRequestHeader(inner) with MaybeUserRequestHeader

/**
 * A HTTP request header that made by a user.
 */
trait UserRequestHeader extends MaybeUserRequestHeader {

  def user: User

  def inner: RequestHeader

  def maybeUser: Try[User] = Success(user)
}

/**
 * Default implementation of [[UserRequestHeader]].
 */
private[security] case class UserRequestHeaderImpl(
  user: User,
  inner: RequestHeader
) extends WrappedRequestHeader(inner) with UserRequestHeader

/**
 * Wrap an existing request header. Useful to extend a request header.
 */
class WrappedRequestHeader(request: RequestHeader) extends RequestHeader {

  override def id = request.id
  override def tags = request.tags
  override def headers = request.headers
  override def queryString = request.queryString
  override def path = request.path
  override def uri = request.uri
  override def method = request.method
  override def version = request.version
  override def remoteAddress = request.remoteAddress
  override def secure = request.secure
  override def clientCertificateChain = request.clientCertificateChain
}