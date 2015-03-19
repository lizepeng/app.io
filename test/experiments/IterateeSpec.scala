package experiments

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.time.NoTimeConversions
import play.api.libs.iteratee._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class IterateeSpec extends Specification with NoTimeConversions {

  "Enumerator" should {

    "be used this way" in new WithApplication {
      Await.result(
        Enumerator(true, true, false, true, true) &>
          Enumeratee.dropWhile(_ == true) &>
          Enumeratee.take(1) |>>>
          Iteratee.getChunks, 10 seconds
      ) mustEqual List(false)
    }
  }
}