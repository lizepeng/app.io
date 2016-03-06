package controllers

import helpers.BaseException
import play.api.data.validation.ValidationError
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.WebSocket.MessageFlowTransformer

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
package object actors {

  case class JsonParseError(e: Seq[(JsPath, Seq[ValidationError])])
    extends BaseException("json.parse.error")

  def caseClassMessageFlowTransformer[A: Format, B: Format](
    implicit message: Messages
  ): MessageFlowTransformer[Try[A], B] = {
    MessageFlowTransformer.jsonMessageFlowTransformer.map(
      in => Json.fromJson[A](in).fold(
        failure => Failure(JsonParseError(failure)),
        success => Success(success)
      ),
      out => Json.toJson(out)
    )
  }
}