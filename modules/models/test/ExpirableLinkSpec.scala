package models

import helpers.ExtCodecs._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class ExpirableLinkSpec extends Specification {

  "length of id" should {

    "be 128" in {
      Codecs.sha2("aaa").length mustEqual 64
      Codecs.sha2("aaa", 512).length mustEqual 128
    }
  }
}