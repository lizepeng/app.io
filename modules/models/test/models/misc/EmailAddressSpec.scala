package models.misc

import java.io._

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.json._

@RunWith(classOf[JUnitRunner])
class EmailAddressSpec extends Specification {

  import EmailAddressSpec._

  "Email Address" should {

    "be able to compare with another Email Address" >> {
      email1 mustEqual email2
      email1 mustNotEqual email3
      email1.self mustEqual "zepeng.li@gmail.com"
      Json.toJson(email1) mustEqual JsString("zepeng.li@gmail.com")
    }

    "be able to serialized" >> {
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(email1)
      bos.toByteArray.length mustNotEqual 0
    }
  }
}

object EmailAddressSpec {

  val email1 = EmailAddress("zepeng.li@gmail.com")
  val email2 = EmailAddress("zepeng.li@gmail.com")
  val email3 = EmailAddress("zepeng.li@qq.com")
}