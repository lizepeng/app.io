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

trait JsonOptionalStringifier[A] extends Stringifier[A] with Logging {

  def jsonFormat: Format[A]

  def optionalFromJson(obj: String): Option[A] = <<(obj).toOption

  def toJson(obj: A): String = >>:(obj)

  def << : (String) => Try[A] = obj => Try(Json.parse(obj)) match {
    case Success(json) => jsonFormat.reads(json) match {
      case JsSuccess(value, _) => Success(value)
      case e: JsError          =>
        Logger.warn(s"Json parse succeeded but read failed. Reverts to default value: ${e.toString}")
        Failure(JsonOptionalStringifier.JsonReadsFailed(e))
    }
    case Failure(e)    =>
      Logger.warn("Json parse failed. Reverts to default value", e)
      Failure(e)
  }

  def >>: : (A) => String = obj => Json.stringify(jsonFormat.writes(obj))
}

object JsonOptionalStringifier {

  case class JsonReadsFailed(e: JsError) extends BaseException("json.reads.failed")

  implicit def jsonStringifier[A](implicit fmt: Format[A]) = {
    new JsonOptionalStringifier[A] {def jsonFormat = fmt}
  }
}