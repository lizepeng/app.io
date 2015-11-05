package helpers

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
trait BootingProcess {

  def onStart[T](awaitable: Awaitable[T]): T = {

    Await.result(awaitable, 1 minute)
  }

}