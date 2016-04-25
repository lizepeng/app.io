package models.summary

import helpers.EnumLikeSpec._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class CountsSpec extends Specification {

  import CountsSpec._

  "Counts" should {

    "be able to calculate correctly" >> {
      val counts = Counts1(Map(Enum1.A -> 1)) + (Enum1.B -> 2)
      counts mustEqual Counts1(Map(Enum1.A -> 1, Enum1.B -> 2))
      val counts1 = Counts1(Map(Enum1.A -> 1)) + (Enum1.B -> -2)
      counts1 mustEqual Counts1(Map(Enum1.A -> 1, Enum1.B -> -2))
      val counts2 = Counts1(Map(Enum1.A -> 1)) + (Enum1.A -> 2)
      counts2 mustEqual Counts1(Map(Enum1.A -> 3))
      val counts3 = Counts1(Map(Enum1.A -> 1)) + (Enum1.A -> -2)
      counts3 mustEqual Counts1(Map(Enum1.A -> -1))
      val counts4 = Counts1(Map(Enum1.A -> 1)) + (Enum1.A -> -1)
      counts4 mustEqual Counts1(Map())
      val counts5 = Counts1(Map(Enum1.A -> 1)) + None
      counts5 mustEqual Counts1(Map(Enum1.A -> 1))
      val counts6 = Counts1(Map(Enum1.A -> 1)) + Some(Enum1.B -> 2)
      counts6 mustEqual Counts1(Map(Enum1.A -> 1, Enum1.B -> 2))
      val counts7 = Counts1(Map(Enum1.A -> 1)) + Some(Enum1.B -> 2)
      counts7 mustEqual Counts1(Map(Enum1.A -> 1, Enum1.B -> 2))
      val counts8 = Counts1(Map(Enum1.A -> 1)) - (Enum1.A -> -2)
      counts8 mustEqual Counts1(Map(Enum1.A -> 3))
      val counts9 = Counts1(Map(Enum1.A -> 1)) - (Enum1.A -> 1)
      counts9 mustEqual Counts1(Map())
      val counts10 = Counts1(Map(Enum1.A -> 1, Enum1.B -> 1, Enum1.Unknown -> 1))
      val counts11 = Counts1(Map(Enum1.A -> 1, Enum1.B -> 2, Enum1.C -> 1))
      (counts10 -- counts11) mustEqual Counts1(Map(Enum1.B -> -1, Enum1.Unknown -> 1, Enum1.C -> -1))
    }
  }
}

object CountsSpec {

  case class Counts1(self: Map[Enum1, Int]) extends Counts[Enum1, Counts1] {

    def copyFrom = copy
  }
}