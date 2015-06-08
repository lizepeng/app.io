package experiments

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.iteratee._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class IterateeSpec extends Specification {

  "Enumerator" should {

    "be used this way" in {
      Await.result(
        Enumerator(true, true, false, true, true) &>
          Enumeratee.dropWhile(_ == true) &>
          Enumeratee.take(1) |>>>
          Iteratee.getChunks, 10.seconds
      ) mustEqual List(false)
    }
  }
}