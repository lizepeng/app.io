package helpers

import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object ExtReads {

  def always[T](default: => T) = new Reads[T] {
    def reads(json: JsValue) = JsSuccess(default)
  }
}