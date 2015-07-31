package controllers

import play.api.http._
import play.api.libs.json._
import play.api.mvc._

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object api_internal {

  /**
   * `Writable` also Pretty Print for `JsValue` values - Json
   */
  implicit def prettyWritableOf_JsValue(implicit codec: Codec): Writeable[JsValue] = {
    import play.api.libs.iteratee.Execution.Implicits.trampoline
    Writeable(jsval => codec.encode(Json.prettyPrint(jsval)))
  }
}