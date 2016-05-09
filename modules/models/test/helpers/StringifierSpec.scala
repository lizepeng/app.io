package helpers

import helpers.StringifierConverts._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.json._

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

  "Map[Any, Stringifier]" can {

    "be converted from Map[Any, String]" in {
      val map1 = Map[Int, String](
        12 -> "12", 13 -> "13", 14 -> "ab"
      )

      map1.valueToType[Int] mustEqual Map(12 -> 12, 13 -> 13)
    }

    "be converted to Map[String, Any]" in {
      val map1 = Map[Int, Int](
        12 -> 12, 13 -> 13
      )

      map1.valueToString mustEqual Map(12 -> "12", 13 -> "13")
    }
  }

  case class Position(x: Int, y: Int)
  implicit val fmt = Json.format[Position]

  "Map[Any, JsonStringifier]" can {

    import JsonOptionalStringifier._

    "be converted from Map[Any, String]" in {
      val map1 = Map[Int, String](
        12 -> """{"x":1,"y":2}""", 13 -> """{"x":1,"y":3}""", 14 -> """{"x":a,"y":b}"""
      )

      map1.valueToType[Position] mustEqual Map(12 -> Position(1, 2), 13 -> Position(1, 3))
    }

    "be converted to Map[String, Any]" in {
      val map1 = Map[Int, Position](
        12 -> Position(1, 2), 13 -> Position(1, 3)
      )

      map1.valueToString mustEqual Map(12 -> """{"x":1,"y":2}""", 13 -> """{"x":1,"y":3}""")
    }
  }

  "Iterable[JsonStringifier]" can {

    import JsonOptionalStringifier._

    "be converted from Iterable[String]" in {
      val list1 = List[String]("""{"x":1,"y":2}""", """{"x":1,"y":3}""", """{"x":a,"y":b}""")
      val set1 = Set[String]("""{"x":1,"y":2}""", """{"x":1,"y":2}""", """{"x":a,"y":b}""")
      val seq1 = Seq[String]("""{"x":1,"y":2}""", """{"x":1,"y":3}""", """{"x":a,"y":b}""")

      list1.elementToType[Position] mustEqual List(Position(1, 2), Position(1, 3))
      set1.elementToType[Position] mustEqual Set(Position(1, 2))
      seq1.elementToType[Position] mustEqual Seq(Position(1, 2), Position(1, 3))
    }

    "be converted to Iterable[String]" in {
      val list1 = List[Position](Position(1, 2), Position(1, 3))
      val set1 = List[Position](Position(1, 2), Position(1, 3))
      val seq1 = List[Position](Position(1, 4), Position(1, 3))

      list1.elementToString mustEqual List("""{"x":1,"y":2}""", """{"x":1,"y":3}""")
      set1.elementToString mustEqual List("""{"x":1,"y":2}""", """{"x":1,"y":3}""")
      seq1.elementToString mustEqual List("""{"x":1,"y":4}""", """{"x":1,"y":3}""")
    }
  }
}