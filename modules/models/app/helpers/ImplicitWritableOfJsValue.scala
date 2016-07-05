package helpers

import play.api.http._
import play.api.libs.json._
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
trait ImplicitWritableOfJsValue {

  implicit def contentTypeOf_JsValue_WithCharset(implicit codec: Codec): ContentTypeOf[JsValue] = {
    ContentTypeOf[JsValue](Some(ContentTypes.withCharset(ContentTypes.JSON)))
  }

  /**
   * `Writable` also Pretty Print for `JsValue` values - Json
   */
  implicit def prettyWritableOf_JsValue(implicit codec: Codec): Writeable[JsValue] = {
    Writeable(jsval => codec.encode(Json.prettyPrint(jsval)))
  }
}