package experiments

import org.joda.time.DateTime
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.time.NoTimeConversions

@RunWith(classOf[JUnitRunner])
class JodaSpec extends Specification with NoTimeConversions {

  "DateTime" should {

    "print itself this way" in {
      println(DateTime.now())
      true mustEqual true
    }
  }
}