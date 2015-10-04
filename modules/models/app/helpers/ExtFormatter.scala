package helpers

import play.api.data.FormError
import play.api.data.format.Formatter

/**
 * @author zepeng.li@gmail.com
 */
object ExtFormatter {

  implicit def anyValFormatter[T](
    wrap: String => T,
    unwrap: T => String
  ): Formatter[T] = new Formatter[T] {

    def bind(key: String, data: Map[String, String]) =
      data.get(key).map(wrap(_)).toRight(Seq(FormError(key, "error.required", Nil)))

    def unbind(key: String, value: T) = Map(key -> unwrap(value))
  }
}