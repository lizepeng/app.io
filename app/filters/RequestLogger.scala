package filters

import play.api.Logger
import play.api.mvc._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class RequestLogger(
  implicit val ec: ExecutionContext
) extends Filter {

  def apply(next: RequestHeader => Future[Result])(
    req: RequestHeader
  ): Future[Result] = {
    if (!req.uri.contains("assets")) {
      Logger.trace(req.uri)
      Logger.trace(req.remoteAddress)
      Logger.trace(req.headers.toString())
    }
    next(req)
  }
}