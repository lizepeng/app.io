package controllers

import helpers._
import models.RateLimits
import org.joda.time.DateTime
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.mvc._
import protocols.ExHeaders
import protocols.JsonProtocol._
import security._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class RateLimitChecker(
  implicit
  val resource: CheckedResource,
  val basicPlayApi: BasicPlayApi,
  val unit: RateLimitUnit,
  val _rateLimits: RateLimits
)
  extends ActionFunction[UserRequest, UserRequest]
  with ExHeaders
  with CanonicalNamed
  with BasicPlayComponents
  with DefaultPlayExecutor
  with I18nSupport
  with AppConfigComponents {

  override val basicName = "rate_limit"

  override def invokeBlock[A](
    req: UserRequest[A],
    block: (UserRequest[A]) => Future[Result]
  ): Future[Result] = {
    val res = resource.name
    val user = req.user

    val now = DateTime.now
    val minutes = now.getMinuteOfHour
    val seconds = now.getSecondOfMinute
    val remaining = (unit.span - minutes % unit.span) * 60 - seconds
    val period_start = now.hourOfDay.roundFloorCopy
      .plusMinutes((minutes / unit.span) * unit.span)

    _rateLimits.get(res, period_start)(user)
      .flatMap { counter =>
      if (counter >= unit.limit) Future.successful {
        Results.TooManyRequest {
          JsonMessage(s"api.$basicName.exceeded")(request2Messages(req))
        }.withHeaders(
            X_RATE_LIMIT_LIMIT -> unit.limit.toString,
            X_RATE_LIMIT_REMAINING -> "0",
            X_RATE_LIMIT_RESET -> remaining.toString
          )
      }
      else
        for {
          ___ <- _rateLimits.inc(res, period_start)(user)
          ret <- block(req)
        } yield {
          ret.withHeaders(
            X_RATE_LIMIT_LIMIT -> unit.limit.toString,
            X_RATE_LIMIT_REMAINING -> (unit.limit - counter - 1).toString,
            X_RATE_LIMIT_RESET -> remaining.toString
          )
        }
    }
  }
}

/** Unit of rate limit
  *
  * @param limit max value of permitted request in a span
  * @param span every n minutes from o'clock
  */
case class RateLimitUnit(limit: Int, span: Int)

trait RateLimitConfig {
  self: CanonicalNamed =>

  def configuration: Configuration

  implicit lazy val rateLimitUnit = RateLimitUnit(
    configuration
      .getInt(s"$canonicalName.rate_limit.limit")
      .orElse(configuration.getInt(s"$packageName.rate_limit.limit"))
      .getOrElse(900),
    configuration
      .getMilliseconds(s"$canonicalName.rate_limit.span")
      .orElse(configuration.getMilliseconds(s"$packageName.rate_limit.span"))
      .map(_ millis)
      .map(_.toMinutes.toInt).getOrElse(15)
  )
}