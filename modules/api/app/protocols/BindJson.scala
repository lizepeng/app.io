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

    def as[T](f: T => Future[Result]): Builder2[T] = new Builder2(part, f)
  }

  class Builder2[T](part: Option[JsObject], f: T => Future[Result]) {

    def recover(handler: ErrorHandler)(
      implicit req: Request[AnyContent],
      reads: Reads[T],
      messages: Messages
    ) = apply(handler)

    def apply(handler: ErrorHandler = PartialFunction.empty)(
      implicit req: Request[AnyContent],
      reads: Reads[T],
      messages: Messages
    ) = BodyIsJsObject { obj =>
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