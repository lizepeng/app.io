package helpers

import play.api.libs.json.JsString

import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
object ExtString {

  implicit class BinaryString(val self: String) extends AnyVal {

    def tryToLong = Try(BigInt(self, 2).toLong)

    def toJson = JsString(self)

    override def toString = self
  }

  object BinaryString {

    def from(long: Long): BinaryString = {
      BinaryString(f"${long.toBinaryString}%64s".replace(' ', '0'))
    }
  }
}