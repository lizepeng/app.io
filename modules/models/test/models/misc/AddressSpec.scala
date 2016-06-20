package models.misc

import java.io._

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.json._

@RunWith(classOf[JUnitRunner])
class AddressSpec extends Specification {

  import AddressSpec._

  "Address" should {

    "be able to compare with another Address" >> {
      address1 mustEqual address2
      address1 mustNotEqual address3
      Json.prettyPrint(Json.toJson(address1)) mustEqual address1Json
    }

    "be able to serialized" >> {
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(address1)
      bos.toByteArray.length mustNotEqual 0
    }
  }
}

object AddressSpec {

  val address1 = Address001(
    province = "Beijing",
    city = "Beijing",
    street1 = "AAA",
    street2 = "BBB",
    postal_code = "1000010",
    country = CountryCode.CN
  )
  val address2 = Address001(
    province = "Beijing",
    city = "Beijing",
    street1 = "AAA",
    street2 = "BBB",
    postal_code = "1000010",
    country = CountryCode.CN
  )
  val address3 = Address002(
    province = "Beijing",
    district = "Beijing",
    street1 = "AAA",
    street2 = "BBB",
    postal_code = "333333",
    country = CountryCode.TH
  )

  val address1Json =
    """{
      |  "province" : "Beijing",
      |  "city" : "Beijing",
      |  "street1" : "AAA",
      |  "street2" : "BBB",
      |  "postal_code" : "1000010",
      |  "country" : "CN"
      |}""".stripMargin
}