import controllers.Files._
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
package object controllers {

  implicit class RequestWithPreviousURI(request: RequestHeader) {

    def previousURI(implicit request: Request[AnyContent]): Option[String] = {
      request.body.asFormUrlEncoded.flatMap(_.get("previous_uri").flatMap(_.headOption))
    }
  }

  def RedirectToPreviousURI(implicit request: Request[AnyContent]): Option[Result] = {
    request.previousURI.map(Redirect(_))
  }
}