import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
package object controllers {

  implicit class RequestWithPreviousURI(req: RequestHeader) {

    def previousURI(implicit req: Request[AnyContent]): Option[String] = {
      req.body.asFormUrlEncoded.flatMap(_.get("previous_uri").flatMap(_.headOption))
    }
  }

  def RedirectToPreviousURI(implicit req: Request[AnyContent]): Option[Result] = {
    req.previousURI.map(Results.Redirect(_))
  }
}