package helpers

import helpers.StringifierMapConverts._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

import scala.util._

@RunWith(classOf[JUnitRunner])
class StringifierSpec extends Specification {

  implicit val intStringifier = new Stringifier[Int] {
    def << : String => Try[Int] = str => Try(str.toInt)
    def >>: : Int => String = _.toString
  }

  "Stringifier" can {

    "convert String to A" in {
      intStringifier << "12" mustEqual Success(12)
      intStringifier << "abc" must failedTry[Int]
      intStringifier <<("12", 13) mustEqual 12
      intStringifier <<("abc", 13) mustEqual 13
      intStringifier <<< "13" mustEqual Some(13)
      intStringifier <<< "abc" mustEqual None
    }

    "convert A to String" in {
      12 >>: intStringifier mustEqual "12"
    }
  }

  "Map[Stringifier, Any]" can {

    "be converted from Map[String, Any]" in {
      val map1 = Map[String, Boolean](
        "12" -> true, "13" -> false, "ab" -> false
      )

      map1.keyToType[Int] mustEqual Map(12 -> true, 13 -> false)
    }

    "be converted to Map[String, Any]" in {
      val map1 = Map[Int, Boolean](
        12 -> true, 13 -> false
      )

      map1.keyToString mustEqual Map("12" -> true, "13" -> false)
    }
  }
}