package models

import models.sys.SysConfigs
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

import scala.concurrent._

@RunWith(classOf[JUnitRunner])
class InternalGroupSpec extends Specification with EmbeddedCassandra {

  val a = InternalGroup.Anyone
  val b = InternalGroup(1)
  val c = InternalGroup(2)

  val x = InternalGroup(-1)
  val y = InternalGroup(19)

  "InternalGroup" should {

    "be able to be calculated correctly" in {

      a.isValid mustEqual true
      b.isValid mustEqual true
      c.isValid mustEqual true
      x.isValid mustEqual false
      y.isValid mustEqual false
      a.toBits.contains(a) mustEqual true
      a.toBits.contains(b) mustEqual false
      (a | b).contains(a) mustEqual true
      (a | b).contains(b) mustEqual true
      (a | b).contains(c) mustEqual false
      (a | b | c).toInternalGroups must haveSize(3)
      (a | b | c).toInternalGroups must contain(a)
      (a | b | c).toInternalGroups must contain(b)
      (a | b | c).toInternalGroups must contain(c)
      (a | b | x) mustEqual (a | b)
      ((a | b) + x) mustEqual (a | b)
      ((a | b) - x) mustEqual (a | b)
      ((a | b | c) - a) mustEqual (c | b)
      ((a | b | c) + a) mustEqual (c | b | a)
    }
  }

  "C* InternalGroups" should {

    "be able to load internal group ids correctly" in {
      implicit val sysConfig = new SysConfigs
      val _groups = new InternalGroups(
        _ => Future(Unit),
        _ => Future(Unit)
      )

      _groups.find(a | b | c).size mustEqual 3
      _groups.find(a | b | c) mustEqual _groups.InternalGroupIds.take(3)
      _groups.AnyoneId mustEqual _groups.InternalGroupIds(InternalGroup.Anyone.code)
    }
  }
}