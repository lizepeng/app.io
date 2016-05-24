package helpers

import helpers.EnumLikeConverts._
import org.junit.runner._
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.i18n.Messages
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class EnumLikeSpec extends Specification with Mockito {

  import EnumLikeSpec._

  "EnumLike" can {

    "be able to generate basicName correctly" in {
      Enum1.basicName mustEqual "EnumLikeSpec.Enum1"
      Inner.Enum2.basicName mustEqual "EnumLikeSpec.Inner.Enum2"
    }

    "be able to compare to other object" in {
      Enum1.A.in(Enum1.A) mustEqual true
      Enum1.A.in(Enum1.A, Enum1.B) mustEqual true
      Enum1.A.in(Enum1.B) mustEqual false
      Enum1.A.in(Enum1.C, Enum1.B) mustEqual false
    }

    "be able to convert to another EnumLike" in {
      val map1 = Map(
        Enum1.A -> 1,
        Enum1.B -> 2,
        Enum1.C -> 2
      )
      val map3 = Map(
        Enum3("A") -> 1,
        Enum3("B") -> 2,
        Enum3("C") -> 2
      )
      val list1 = List(Enum1.A, Enum1.B, Enum1.C)
      map1.keyToEnum[Enum3] mustEqual map3
      list1.elementToString mustEqual List("A", "B", "C")
      List("A", "B", "C").elementToEnum[Enum1] mustEqual list1
    }
  }

  "EnumLike.Value" can {

    "be read from/written to Json" >> {
      Json.fromJson[Enum1](Json.toJson(Enum1.A)).get mustEqual Enum1.A
    }
  }

  "EnumLike#toJson" can {

    "convert values to Json format" in {
      implicit val msg = mock[Messages]
      msg.apply("EnumLikeSpec.Enum1.A") returns "AA"
      msg.apply("EnumLikeSpec.Enum1.B") returns "BB"
      msg.apply("EnumLikeSpec.Enum1.C") returns "CC"
      msg.apply("EnumLikeSpec.Enum1.A.1") returns "AA1"
      msg.apply("EnumLikeSpec.Enum1.B.1") returns "BB1"
      msg.apply("EnumLikeSpec.Enum1.C.1") returns "CC1"
      val filter1: Enum1 => Boolean = {
        case Enum1.A => true
        case _       => false
      }
      Enum1.toJson() mustEqual
        """{
          |  "A" : "AA",
          |  "B" : "BB",
          |  "C" : "CC"
          |}""".stripMargin
      Enum1.toJson(postfix = "1") mustEqual
        """{
          |  "A" : "AA1",
          |  "B" : "BB1",
          |  "C" : "CC1"
          |}""".stripMargin

      Enum1.toJson(filter = filter1, postfix = "1") mustEqual
        """{
          |  "A" : "AA1"
          |}""".stripMargin
    }
  }
}

object EnumLikeSpec {

  case class Enum1(self: String) extends AnyVal with EnumLike.Value

  object Enum1 extends EnumLike.Definition[Enum1] {

    val A       = Enum1("A")
    val B       = Enum1("B")
    val C       = Enum1("C")
    val Unknown = Enum1("Unknown")

    val values = Seq(A, B, C)

    implicit def self = this
  }

  object Inner {

    case class Enum2(self: String) extends AnyVal with EnumLike.Value

    object Enum2 extends EnumLike.Definition[Enum2] {

      val A       = Enum2("A")
      val B       = Enum2("B")
      val Unknown = Enum2("Unknown")

      val values = Seq(A, B)

      implicit def self = this
    }
  }

  case class Enum3(self: String) extends AnyVal with EnumLike.Value

  object Enum3 extends EnumLike.Definition[Enum3] {

    val Unknown = Enum3("Unknown")

    val values = Enum1.values.map(v => Enum3(v.self))

    implicit def self = this
  }
}