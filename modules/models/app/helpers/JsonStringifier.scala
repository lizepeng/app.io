package helpers

import play.api.libs.json.{Format, Json}

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait JsonStringifier[A] {

  def jsonFormat: Format[A]

  def default: A

  def fromJson(obj: String): A = Try(Json.parse(obj)) match {
    case Success(json) => jsonFormat.reads(json).getOrElse(default)
    case Failure(e)    => default
  }

  def toJson(obj: A): String = Json.stringify(jsonFormat.writes(obj))
}