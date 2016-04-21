package helpers

import play.api.libs.json._

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait JsonStringifier[A] extends JsonOptionalStringifier[A] {

  def default: A

  def fromJson(obj: String): A = optionalFromJson(obj).getOrElse(default)
}

trait JsonOptionalStringifier[A] extends Logging {

  def jsonFormat: Format[A]

  def optionalFromJson(obj: String): Option[A] = Try(Json.parse(obj)) match {
    case Success(json) => jsonFormat.reads(json) match {
      case JsSuccess(value, _) => Some(value)
      case e: JsError          =>
        Logger.warn(s"Json parse succeeded but read failed. Reverts to default value: ${e.toString}")
        None
    }
    case Failure(e)    =>
      Logger.warn("Json parse failed. Reverts to default value", e)
      None
  }

  def toJson(obj: A): String = Json.stringify(jsonFormat.writes(obj))
}

object JsonOptionalStringifier {

  implicit def jsonStringifier[A](implicit fmt: Format[A]) = {
    new JsonOptionalStringifier[A] {def jsonFormat = fmt}
  }
}