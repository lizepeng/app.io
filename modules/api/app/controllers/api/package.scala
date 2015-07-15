package controllers

import models.TimeBased
import play.api.http._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc._
import protocols.JsonProtocol._
import security.UserRequest

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object api {

  /**
   * `Writable` also Pretty Print for `JsValue` values - Json
   */
  implicit def prettyWritableOf_JsValue(implicit codec: Codec): Writeable[JsValue] = {
    import play.api.libs.iteratee.Execution.Implicits.trampoline
    Writeable(jsval => codec.encode(Json.prettyPrint(jsval)))
  }

  object BodyIsJsObject {

    def apply(f: JsObject => Future[Result])(
      implicit req: UserRequest[AnyContent], messages: Messages
    ): Future[Result] = {
      req.body.asJson match {
        case Some(obj: JsObject) => f(obj)
        case _                   =>
          Future.successful(Results.BadRequest(WrongTypeOfJson()))
      }
    }
  }

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

  object NotModifiedOrElse extends HeaderNames {

    def apply[T <: TimeBased](block: T => Result)(
      implicit req: RequestHeader, messages: Messages
    ): T => Result = { entity =>
      val updated_at = entity.updated_at.withMillisOfSecond(0)
      req.headers.get(IF_MODIFIED_SINCE)
        .map(dateFormat.parseDateTime) match {
        case Some(d) if !d.isBefore(updated_at) =>
          Results.NotModified
        case _                                  =>
          block(entity).withHeaders(
            LAST_MODIFIED -> dateFormat.print(updated_at)
          )
      }
    }
  }

}