package controllers

import controllers.api.AccessControls._
import helpers.BaseException
import play.api.data.validation.ValidationError
import play.api.http._
import play.api.i18n.{Lang, Messages => MSG}
import play.api.libs.json._
import play.api.mvc._
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
    Writeable(jsval => codec.encode(Json.prettyPrint(jsval)))
  }

  /**
   * Just like an HTML error page shows a useful error message to a visitor.
   */
  object JsonClientErrors {

    def apply(
      jse: JsError
    )(implicit lang: Lang): JsObject = apply(jse.errors)

    def apply(
      errors: Seq[(JsPath, Seq[ValidationError])]
    )(implicit lang: Lang): JsObject = Json.obj(
      "message" -> MSG("api.json.validation.failed"),
      "errors" -> JsArray {
        errors.map { case (path, errs) =>
          Json.obj(
            "field" ->
              path.path.map(
                _.toJsonString.tail
              ).mkString("."),
            "errors" -> JsArray {
              errs.map { err =>
                Json.obj(
                  "code" -> err.message,
                  "message" -> MSG(err.message, err.args: _*)
                )
              }
            }
          )
        }
      }
    )

  }

  object JsonMessage {

    def apply(e: BaseException)(implicit lang: Lang): JsObject = {
      generate(e.message)
    }

    def apply(key: String, args: Any*)(implicit lang: Lang): JsObject = {
      generate(MSG(key, args))
    }

    private def generate(msg: String): JsObject = {
      Json.obj("message" -> msg)
    }
  }

  object WrongTypeOfJson {

    def apply()(implicit lang: Lang): JsObject =
      Json.obj("message" -> MSG("api.json.body.wrong.type"))
  }

  object BodyIsJsObject {

    def apply(f: JsObject => Future[Result])(
      implicit req: UserRequest[AnyContent]
    ): Future[Result] = {
      req.body.asJson match {
        case Some(obj: JsObject) => f(obj)
        case _                   => Future.successful(BadRequest(WrongTypeOfJson()))
      }
    }
  }

  object BindJson {

    class Handling(part: Option[JsObject]) {

      def as[T](f: T => Future[Result])(
        implicit req: UserRequest[AnyContent], reads: Reads[T]
      ): Future[Result] = BodyIsJsObject { obj =>
        part.map(_ ++ obj).getOrElse(obj).validate[T].fold(
          failure => Future.successful(
            UnprocessableEntity(JsonClientErrors(failure))
          ),
          success => f(success)
        )
      }
    }

    def and(part: JsObject) = new Handling(Some(part))

    def apply() = new Handling(None)

  }

}