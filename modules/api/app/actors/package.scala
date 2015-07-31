import protocols.JsonProtocol
import helpers.BaseException
import play.api.data.validation.ValidationError
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter
import JsonProtocol._

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
package object actors {

  case class JsonParseError(e: Seq[(JsPath, Seq[ValidationError])])
    extends BaseException("json.parse.error")

  def jsonFrame[A: Format](implicit message: Messages): FrameFormatter[Try[A]] =
    FrameFormatter.stringFrame.transform[Try[A]](
      _ match {
        case Success(a)                => Json.stringify(Json.toJson(a))
        case Failure(e: BaseException) => Json.stringify(JsonMessage(e))
        case Failure(e: Throwable)     => Json.stringify(JsonMessage(e.getMessage))
      },
      in => Try(Json.parse(in)) match {
        case Success(json)         => Json.fromJson[A](json).fold(
          failure => Failure(JsonParseError(failure)),
          success => Success(success)
        )
        case Failure(e: Throwable) => Failure(e)
      }
    )
}