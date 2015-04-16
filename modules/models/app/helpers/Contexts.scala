package helpers

import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.ExecutionContext

/**
 * @author zepeng.li@gmail.com
 */
object Contexts {

  implicit val trafficShaperContext: ExecutionContext =
    Akka.system.dispatchers.lookup("traffic-shaper-context")
}