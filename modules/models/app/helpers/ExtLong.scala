package helpers

import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object ExtLong {

  implicit class BitSet(val self: Long) extends AnyVal {

    def toIndices: Seq[Int] = BitSet.toIndices(self, 63)
  }

  object BitSet {

    def from(indices: Seq[Int]): BitSet =
      BitSet(indices.map(1L << _).sum)

    def toIndices(self: Long, max: Int): Seq[Int] = {
      (0 to max).collect {
        case i if {val j = 1L << i; (self & j) == j} => i
      }
    }

    implicit val jsonFormat = new Format[BitSet] {
      def reads(json: JsValue): JsResult[BitSet] = {
        Json.fromJson[Seq[Int]](json).map(BitSet.from)
      }
      def writes(o: BitSet): JsValue = {
        Json.toJson(o.toIndices)
      }
    }
  }
}