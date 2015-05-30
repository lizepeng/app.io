package protocols

import helpers.BaseException
import play.api.data.validation.ValidationError
import play.api.i18n.{Lang, Messages}
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object JsonProtocol {

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
      generate(e.message)
    }

    def apply(key: String, args: Any*)(implicit lang: Lang): JsObject = {
      generate(Messages(key, args))
    }

    private def generate(msg: String): JsObject = {
      Json.obj("message" -> msg)
    }
  }

  object WrongTypeOfJson {

    def apply()(implicit lang: Lang): JsObject =
      JsonMessage.apply("api.json.body.wrong.type")
  }

}