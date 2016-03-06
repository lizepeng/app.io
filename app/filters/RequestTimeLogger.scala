package filters

import akka.stream.Materializer
import play.api.Logger
import play.api.mvc._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class RequestTimeLogger(
  implicit
  val ec: ExecutionContext,
  val mat: Materializer
) extends Filter {

  def apply(next: RequestHeader => Future[Result])(
    req: RequestHeader
  ): Future[Result] = {
    def now = System.currentTimeMillis
    val start = now
    next(req).map { result =>
      val took = now - start
      if (!req.uri.contains("assets")) {
        Logger.trace(
          f"${result.header.status}, took $took%4d ms, ${req.method} ${req.uri}"
        )
      }
      result.withHeaders("Request-Time" -> took.toString)
    }
  }
}