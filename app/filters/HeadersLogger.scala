package filters

import play.api.Logger
import play.api.mvc._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class HeadersLogger(
  implicit val ec: ExecutionContext
) extends Filter {

  def apply(nextFilter: RequestHeader => Future[Result])
      (requestHeader: RequestHeader): Future[Result] = {

    nextFilter(requestHeader).map { result =>


      if (!requestHeader.uri.contains("assets")) {
        Logger.trace(requestHeader.headers.toString())
      }
      result
    }
  }
}