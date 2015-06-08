package experiments

import org.joda.time.DateTime
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class JodaSpec extends Specification {

  "DateTime" should {

    "print itself this way" in {
      ConsoleLogger.debug(DateTime.now().toString)
      true mustEqual true
    }
  }
}