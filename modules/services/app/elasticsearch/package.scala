import com.sksamuel.elastic4s.source._
import models.{Group, User}
import play.api.libs.json._

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object elasticsearch {

  case class JsonDocSource(jsval: JsValue) extends DocumentSource {

    val json = jsval.toString()
  }

  implicit def GroupToJsonDocSource(g: Group): JsonDocSource =
    JsonDocSource(Json.toJson(g))

  implicit def UserToJsonDocSource(u: User): JsonDocSource =
    JsonDocSource(Json.toJson(u.toUserInfo))
}