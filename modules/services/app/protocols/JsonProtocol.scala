package protocols

import helpers.BaseException
import play.api.data.validation.ValidationError
import play.api.i18n.Messages
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
    )(implicit messages: Messages): JsObject = apply(jse.errors)

    def apply(
      errors: Seq[(JsPath, Seq[ValidationError])]
    )(implicit messages: Messages): JsObject = Json.obj(
      "message" -> messages("api.json.validation.failed"),
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
                  "message" -> messages(err.message, err.args: _*)
                )
              }
            }
          )
        }
      }
    )

  }

  object JsonMessage {

    def apply(e: BaseException)(
      implicit messages: Messages
    ): JsObject = {
      generate(e.message)
    }

    def apply(key: String, args: Any*)(
      implicit messages: Messages
    ): JsObject = {
      generate(messages(key, args))
    }

    private def generate(msg: String): JsObject = {
      Json.obj("message" -> msg)
    }
  }

  object WrongTypeOfJson {

    def apply()(implicit messages: Messages): JsObject =
      JsonMessage.apply("api.json.body.wrong.type")
  }

}