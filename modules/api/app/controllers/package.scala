package controllers

import helpers.BaseException
import org.elasticsearch.action.search.SearchResponse
import play.api.data.validation.ValidationError
import play.api.http._
import play.api.i18n.{Lang, Messages}
import play.api.libs.json._
import play.api.mvc.Codec

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
   * `Writable` for `SearchResponse` values - Json
   */
  implicit def writableOf_SearchResponse(implicit codec: Codec): Writeable[SearchResponse] = {
    Writeable(response => codec.encode(response.toString))
  }

  /**
   * Default content type for `SearchResponse` values (`application/json`).
   */
  implicit def contentTypeOf_SearchResponse(implicit codec: Codec): ContentTypeOf[SearchResponse] =
    ContentTypeOf[SearchResponse](Some(ContentTypes.JSON))

  /**
   * Just like an HTML error page shows a useful error message to a visitor.
   */
  object JsonClientErrors {

    def apply(
      errors: Seq[(JsPath, Seq[ValidationError])]
    )(implicit lang: Lang): JsObject = Json.obj(
      "message" -> Messages("api.json.validation.failed"),
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
                  "message" -> Messages(err.message, err.args: _*)
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
      apply(e.message(lang))
    }

    def apply(msg: String): JsObject = {
      Json.obj("message" -> msg)
    }
  }

  object WrongTypeOfJSON {

    def apply()(implicit lang: Lang): JsObject =
      Json.obj("message" -> Messages("api.json.body.wrong.type"))
  }

}