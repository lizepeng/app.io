package security

import controllers._
import helpers._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object AuthCheck
  extends ActionFilter[UserRequest] with ModuleLike {

  override val moduleName = "auth_check"

  case class Unauthorized()
    extends BaseException(msg_key("unauthorized"))

  /**
   * access denied
   *
   * @param req
   * @return
   */
  def onUnauthorized(req: RequestHeader) = Results.Redirect(routes.Sessions.nnew())

  override protected def filter[A](
    req: UserRequest[A]
  ): Future[Option[Result]] = {
    Future.successful(
      req.user match {
        case Some(u) => None
        case None    => Some(onUnauthorized(req))
      }
    )
  }

}