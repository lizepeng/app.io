package models

import helpers._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.time.NoTimeConversions
import play.api.libs.Crypto

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class ExpirableLinkSpec extends Specification with NoTimeConversions {

  "length of id" should {

    "be 128" in {
      Crypto.sha2("aaa").length mustEqual 64
      Crypto.sha2("aaa", 512).length mustEqual 128
    }
  }
}