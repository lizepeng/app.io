import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {
//  "Application" should {
//
//    "send 404 on a bad request" in new WithRealApplication {
//      route(FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
//    }
//
//    "render the index page" in new WithRealApplication {
//      val home = route(FakeRequest(GET, "/")).get
//
//      status(home) must equalTo(OK)
//      contentType(home) must beSome.which(_ == "text/html")
//      contentAsString(home) must contain("Your new application is ready.")
//    }
//  }
}

//class WithRealApplication extends WithApplicationLoader(new AppLoader)