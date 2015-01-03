package controllers.helpers

import controllers._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object AuthCheck
  extends ActionFilter[UserRequest] {

  /**
   * access denied
   *
   * @param request
   * @return
   */
  def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Sessions.nnew())

  override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] = {
    Future.successful(
      request.user match {
        case Some(u) => None
        case None    => Some(onUnauthorized(request))
      }
    )
  }
}