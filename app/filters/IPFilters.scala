package filters

import java.net.InetAddress

import helpers.ExtRequest._
import helpers._
import models.IPRateLimits
import org.joda.time.DateTime
import play.api.mvc._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util._

/**
 * Abstract ip filter class, for blocking abusing service
 *
 * @author zepeng.li@gmail.com
 */
abstract class AbstractIPFilter(
  shouldBlock: InetAddress => Boolean
) extends Filter {

  def apply(next: RequestHeader => Future[Result])(
    req: RequestHeader
  ): Future[Result] = req.clientIP.filter(
    ip => !shouldBlock(ip)
  ) match {
    case Success(ip) => next(req)
    case Failure(_)  => Future.successful(Results.NotFound)
  }
}

class LoopbackIPFilter
  extends AbstractIPFilter(ip => ip.isLoopbackAddress)

class InvalidIPFilter
  extends AbstractIPFilter(ip => ip.isSiteLocalAddress || ip.isMulticastAddress)

class RateLimitExceededIPFilter(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _ipRateLimits: IPRateLimits
)
  extends Filter
  with BasicPlayComponents
  with DefaultPlayExecutor {

  // default 90000 requests per 15 minutes
  val limit = configuration
    .getInt("app.http.ip_filter.limit")
    .getOrElse(90000)

  val span = configuration
    .getMilliseconds("app.http.ip_filter.span")
    .map(_ millis)
    .map(_.toMinutes.toInt).getOrElse(15)

  def apply(next: RequestHeader => Future[Result])(
    req: RequestHeader
  ): Future[Result] = {

    req.clientIP match {
      case Success(ip) =>
        val now = DateTime.now
        val minutes = now.getMinuteOfHour
        val period_start = now.hourOfDay.roundFloorCopy
          .plusMinutes((minutes / span) * span)

        _ipRateLimits.get(ip, period_start)
          .flatMap { counter =>
          if (counter >= limit) Future.successful {
            Results.TooManyRequest
          } else if (counter * 5 > limit * 4) {
            // greater than 80%
            _ipRateLimits.inc(ip, period_start).flatMap(_ => next(req))
          } else {
            _ipRateLimits.inc(ip, period_start)
            next(req)
          }
        }
      case Failure(_)  => Future.successful(Results.TooManyRequest)
    }
  }
}