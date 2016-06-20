package models.misc

import org.junit.runner._
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.i18n.Messages

@RunWith(classOf[JUnitRunner])
class CountryCodeSpec extends Specification with Mockito {

  implicit val msg = mock[Messages]
  msg.apply("country.CN") returns "China"

  "CountryCode" should {

    "be able to generate the map of all country codes" >> {
      CountryCode.toJson(prefix = Some("country")).contains("China") mustEqual true
      CountryCode.toJson(prefix = Some("language")).contains("China") mustEqual false
    }
  }
}