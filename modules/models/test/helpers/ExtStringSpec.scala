package helpers

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class ExtStringSpec extends Specification {

  "ExtString" can {

    "convert camel to underscores" in {
      ExtString.camelToUnderscore("camelToUnderscores") mustEqual "camel_to_underscores"
      ExtString.camelToUnderscore("FindBy") mustEqual "find_by"
      ExtString.camelToUnderscore("Find") mustEqual "find"
      ExtString.camelToUnderscore("find") mustEqual "find"
    }
  }
}