package controllers.internal_api

import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object AuthCheck extends AuthenticationCheck {

  override def onUnauthenticated(req: RequestHeader) = Results.NotFound
}