package filters

import play.api.Logger
import play.api.mvc._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class RequestTimeLogger(
  implicit val ec: ExecutionContext
) extends Filter {

  def apply(
    nextFilter: RequestHeader => Future[Result]
  )(
    requestHeader: RequestHeader
  ): Future[Result] = {
    def now = System.currentTimeMillis

    val startTime = now
    nextFilter(requestHeader).map { result =>
      val endTime = now
      val requestTime = endTime - startTime
      if (!requestHeader.uri.contains("assets")) {
        Logger.trace(
          f"${result.header.status}, took $requestTime%4d ms, ${requestHeader.method} ${requestHeader.uri}"
        )
      }
      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}