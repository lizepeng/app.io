package helpers

import helpers.ExtLong.BitSet
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class ExtLongSpec extends Specification {

  "BitSet" can {

    "be converted to Json" in {
      val bitSet: BitSet = BitSet((1L << 63) + (1L << 34))
      Json.toJson(bitSet).toString() mustEqual "[34,63]"
    }

    "be converted from Json" in {
      val json = Json.parse("""[3,32]""")
      Json.fromJson[BitSet](json).get mustEqual BitSet((1L << 32) + (1L << 3))
    }

    "BitSet(0)" can {

      "be converted to Json" in {
        val bitSet: BitSet = BitSet(0)
        Json.toJson(bitSet).toString() mustEqual "[]"
      }

      "be converted from Json" in {
        val json = Json.parse("""[]""")
        Json.fromJson[BitSet](json).get mustEqual BitSet(0)
      }
    }

    "BitSet(1)" can {

      "be converted to Json" in {
        val bitSet: BitSet = BitSet(1)
        Json.toJson(bitSet).toString() mustEqual "[0]"
      }

      "be converted from Json" in {
        val json = Json.parse("""[0]""")
        Json.fromJson[BitSet](json).get mustEqual BitSet(1)
      }
    }

    "BitSet(-1)" can {
      val bitSet: BitSet = BitSet(-1L)
      val json = (0 to 63).mkString("[", ",", "]")

      "be converted to Json" in {
        Json.toJson(bitSet).toString() mustEqual json
      }

      "be converted from Json" in {
        Json.fromJson[BitSet](Json.parse(json)).get mustEqual bitSet
      }
    }
  }
}