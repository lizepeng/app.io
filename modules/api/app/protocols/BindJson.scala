package protocols

import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc._
import JsonProtocol.JsonClientErrors
import security.UserRequest

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object BindJson {

  class Handling(part: Option[JsObject]) {

    def as[T](f: T => Future[Result])(
      implicit req: UserRequest[AnyContent],
      reads: Reads[T],
      messages: Messages
    ): Future[Result] = BodyIsJsObject { obj =>
      part.map(_ ++ obj).getOrElse(obj).validate[T].fold(
        failure => Future.successful(
          Results.UnprocessableEntity(JsonClientErrors(failure))
        ),
        success => f(success)
      )
    }
  }

  def and(part: JsObject) = new Handling(Some(part))

  def apply() = new Handling(None)
}