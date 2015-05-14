import java.util.UUID

import play.api.data.FormError
import play.api.data.format.{Formats, Formatter}
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

  implicit def uuidFormat: Formatter[UUID] = new Formatter[UUID] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[UUID]
          .either(UUID.fromString(s))
          .left.map(e => Seq(FormError(key, "error.uuid", Nil)))
      }
    }

    def unbind(key: String, value: UUID) = Map(key -> value.toString)
  }
}