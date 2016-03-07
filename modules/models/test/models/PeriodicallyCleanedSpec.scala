package models

import com.datastax.driver.core.utils._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

import scala.concurrent._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class PeriodicallyCleanedSpec extends Specification with EmbeddedCassandra {

  "C* PeriodicallyCleaned" should {

    "be able to be written" in {
      val periodicallyCleaned = new PeriodicallyCleaned

      val id = UUIDs.timeBased()

      val future = for {
        _ <- periodicallyCleaned.scheduleHourly(id, "key")
        _ <- periodicallyCleaned.scheduleDaily(id, "key")
        _ <- periodicallyCleaned.scheduleMonthly(id, "key")
        h <- periodicallyCleaned.isScheduledHourly(id, "key")
        d <- periodicallyCleaned.isScheduledDaily(id, "key")
        w <- periodicallyCleaned.isScheduledWeekly(id, "key")
        m <- periodicallyCleaned.isScheduledMonthly(id, "key")
      } yield (h, d, w, m)

      val ret = Await.result(future, 5.seconds)
      ret mustEqual(true, true, false, true)
    }
  }
}