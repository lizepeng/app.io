package helpers

import play.api.libs.json._

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait JsonStringifier[A] extends Logging {

  def jsonFormat: Format[A]

  def default: A

  def fromJson(obj: String): A = Try(Json.parse(obj)) match {
    case Success(json) => jsonFormat.reads(json) match {
      case JsSuccess(value, _) => value
      case e: JsError          =>
        Logger.warn(s"Json parse succeeded but read failed. Reverts to default value: ${e.toString}")
        default
    }
    case Failure(e)    =>
      Logger.warn("Json parse failed. Reverts to default value", e)
      default
  }

  def toJson(obj: A): String = Json.stringify(jsonFormat.writes(obj))
}