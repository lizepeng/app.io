package plugins.akka.serialization.json

import org.joda.time._

/**
 * @author zepeng.li@gmail.com
 */
object JodaSerializer extends PartialSerializer {

  val JodaDateTime  = "joda.DateTime:1"
  val JodaLocalDate = "joda.LocalDate:1"

  def manifest: PartialFunction[AnyRef, String] = {
    case _: DateTime  => JodaDateTime
    case _: LocalDate => JodaLocalDate
  }

  def toBinary: PartialFunction[AnyRef, Array[Byte]] = {
    case o: DateTime  => toBinaryJson[DateTime](o)
    case o: LocalDate => toBinaryJson[LocalDate](o)
  }

  def fromBinary: PartialFunction[(Array[Byte], String), AnyRef] = {
    case (bytes, JodaDateTime)  => fromBinaryJson[DateTime](bytes)
    case (bytes, JodaLocalDate) => fromBinaryJson[LocalDate](bytes)
  }
}