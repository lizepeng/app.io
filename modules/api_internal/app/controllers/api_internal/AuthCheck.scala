package controllers.api_internal

import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object AuthCheck extends AuthenticationCheck {

  override def onUnauthorized(req: RequestHeader) = Results.NotFound
}