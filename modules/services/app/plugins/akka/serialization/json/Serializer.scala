package plugins.akka.serialization.json

import akka.serialization._

/**
 * @author zepeng.li@gmail.com
 */
class Serializer extends SerializerWithStringManifest {

  def identifier: Int = 2016829

  def manifest(o: AnyRef): String =
    serializers.map(_.manifest).reduce(_ orElse _).apply(o)

  def toBinary(o: AnyRef): Array[Byte] = {
    serializers.map(_.toBinary).reduce(_ orElse _).apply(o)
  }

  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    serializers.map(_.fromBinary).reduce(_ orElse _).apply((bytes, manifest))
  }

  val serializers: Seq[PartialSerializer] =
    Seq(
      JodaSerializer
    )
}