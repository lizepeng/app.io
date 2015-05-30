import helpers.BaseException
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
package object actors {

  case class JsonParseError(e: Seq[(JsPath, Seq[ValidationError])])
    extends BaseException("json.parse.error")

  def classFrame[A: Format]: FrameFormatter[Try[A]] =
    FrameFormatter.stringFrame.transform[Try[A]](
      _ match {
        case Success(a)            => Json.stringify(Json.toJson(a))
        case Failure(e: Throwable) => Json.stringify(Json.obj("error" -> e.getMessage))
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