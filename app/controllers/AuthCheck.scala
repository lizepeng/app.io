package controllers

import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object AuthCheck extends AuthorizationCheck {

  override def onUnauthorized(req: RequestHeader) =
    Results.Redirect(routes.Sessions.nnew())
}