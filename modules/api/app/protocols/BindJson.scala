package protocols

import play.api.data.validation.ValidationError
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc._
import protocols.JsonProtocol.JsonClientErrors

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object BindJson {

  type ErrorHandler = PartialFunction[Seq[(JsPath, Seq[ValidationError])], Result]

  class Builder1(part: Option[JsObject]) {

    def as[T]: Builder2[T] = new Builder2(part)
  }

  class Builder2[T](part: Option[JsObject]) {

    def apply(f: T => Result) = new Builder3[T](part, t => Future.successful(f(t)))
    def async(f: T => Future[Result]) = new Builder3[T](part, f)
  }

  class Builder3[T](part: Option[JsObject], f: T => Future[Result]) {

    def recover(handler: ErrorHandler)(
      implicit req: Request[AnyContent],
      reads: Reads[T],
      messages: Messages
    ) = apply(handler)

    def apply(handler: ErrorHandler = PartialFunction.empty)(
      implicit req: Request[AnyContent],
      reads: Reads[T],
      messages: Messages
    ) = BodyIsJsObject.async { obj =>
      part.map(_ ++ obj).getOrElse(obj).validate[T].fold(
        failure => Future.successful(
          (handler orElse ({
            case es => Results.UnprocessableEntity(JsonClientErrors(es))
          }: ErrorHandler)) (failure)
        ),
        success => f(success)
      )
    }
  }

  def and(part: JsObject) = new Builder1(Some(part))

  def apply() = new Builder1(None)
}