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
    case Success(json) => jsonFormat.reads(json).getOrElse(default)
    case Failure(e)    =>
      Logger.warn("Json parse failed and reverts to default value", e)
      default
  }

  def toJson(obj: A): String = Json.stringify(jsonFormat.writes(obj))
}