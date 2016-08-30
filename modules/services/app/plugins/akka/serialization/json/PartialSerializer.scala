package plugins.akka.serialization.json

import java.nio.charset._

import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
trait PartialSerializer {

  val UTF_8 = StandardCharsets.UTF_8
  def manifest: PartialFunction[AnyRef, String]
  def toBinary: PartialFunction[AnyRef, Array[Byte]]
  def fromBinary: PartialFunction[(Array[Byte], String), AnyRef]

  def toBinaryJson[T](o: T)(implicit fmt: Format[T]): Array[Byte] = {
    Json.stringify(Json.toJson(o)).getBytes(UTF_8)
  }
  def fromBinaryJson[T](bytes: Array[Byte])(implicit fmt: Format[T]): T = {
    Json.fromJson[T](Json.parse(bytes)).get
  }
}