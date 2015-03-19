package security

import controllers._
import helpers.Logging
import models.AccessControl
import models.AccessControl.Denied
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object PermCheck extends Logging {

  def apply(
    resource: String,
    action: String,
    onDenied: RequestHeader => Result
  ) = AuthCheck andThen new ActionFilter[UserRequest] {

    override protected def filter[A](request: UserRequest[A]): Future[Option[Result]] = {
      val u = request.user.get

      AccessControl.find(resource, action, u.id)
        .recoverWith {
        case e: Denied =>
          Logger.trace(e.reason)
          AccessControl.find(resource, action, u.internal_groups)
      }.recoverWith {
        case e: Denied =>
          Logger.trace(e.reason)
          AccessControl.find(resource, action, u.external_groups)
      }.recoverWith {
        case e: Denied =>
          Logger.trace(e.reason)
          Future.successful(false)
      }.map {
        case true  => None
        case false => Some(onDenied(request))
      }
    }
  }
}