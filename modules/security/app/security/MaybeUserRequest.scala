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