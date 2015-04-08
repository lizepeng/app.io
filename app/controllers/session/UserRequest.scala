package controllers

import models._
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
class UserRequest[A](
  val user: Option[User],
  req: Request[A]
) extends WrappedRequest[A](req) {
}