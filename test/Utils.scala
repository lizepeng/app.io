import org.openqa.selenium.phantomjs._
import play.api._
import play.api.test._

/**
 * @author zepeng.li@gmail.com
 */
object Utils {

  def context: ApplicationLoader.Context =
    ApplicationLoader.createContext(
      new Environment(
        new java.io.File("."),
        ApplicationLoader.getClass.getClassLoader,
        Mode.Test
      )
    )

  class WithRealApplication extends WithApplicationLoader(new AppLoader)

  class WithPhantomJS extends WithBrowser(new PhantomJSDriver, new AppLoader().load(context))
}