package controllers

import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.Codec

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object api {

  /**
   * `Writable` also Pretty Print for `JsValue` values - Json
   */
  implicit def prettyWritableOf_JsValue(implicit codec: Codec): Writeable[JsValue] = {
    Writeable(jsval => codec.encode(Json.prettyPrint(jsval)))
  }
}
