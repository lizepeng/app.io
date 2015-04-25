import com.sksamuel.elastic4s.source._
import models.{Group, User}
import org.elasticsearch.action.search.SearchResponse
import play.api.http._
import play.api.libs.json._
import play.api.mvc.Codec

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

  /**
   * `Writable` for `SearchResponse` values - Json
   */
  implicit def writableOf_SearchResponse(implicit codec: Codec): Writeable[SearchResponse] = {
    Writeable(response => codec.encode(response.toString))
  }

  /**
   * Default content type for `SearchResponse` values (`application/json`).
   */
  implicit def contentTypeOf_SearchResponse(implicit codec: Codec): ContentTypeOf[SearchResponse] =
    ContentTypeOf[SearchResponse](Some(ContentTypes.JSON))

}