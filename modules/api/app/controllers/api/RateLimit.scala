package controllers.api

import helpers._
import models.RateLimitRepo
import org.joda.time.DateTime
import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import protocols.JsonProtocol._
import security._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class RateLimit(resource: CheckedResource)(
  implicit
  val messagesApi: MessagesApi,
  val configuration: Configuration,
  val rateLimit: RateLimitRepo
)
  extends ActionFunction[UserRequest, UserRequest]
  with ExHeaders
  with CanonicalNamed
  with AppConfig
  with I18nSupport {

  override val basicName = "rate_limit"

  val limit = config.getInt("limit").getOrElse(50)
  val span  = config.getInt("span").getOrElse(15)

  override def invokeBlock[A](
    req: UserRequest[A],
    block: (UserRequest[A]) => Future[Result]
  ): Future[Result] = {
    val res = resource.name
    val user = req.user

    val now = DateTime.now
    val minutes = now.getMinuteOfHour
    val seconds = now.getSecondOfMinute
    val remaining = (span - minutes % span) * 60 - seconds
    val period_start = now.hourOfDay.roundFloorCopy
      .plusMinutes((minutes / span) * span)

    rateLimit.get(res, period_start)(user)
      .flatMap { counter =>
      if (counter >= limit) Future.successful {
        Results.TooManyRequest {
          JsonMessage(s"api.$basicName.exceeded")
        }.withHeaders(
            X_RATE_LIMIT_LIMIT -> limit.toString,
            X_RATE_LIMIT_REMAINING -> "0",
            X_RATE_LIMIT_RESET -> remaining.toString
          )
      }
      else
        for {
          ___ <- rateLimit.inc(res, period_start)(user)
          ret <- block(req)
        } yield {
          ret.withHeaders(
            X_RATE_LIMIT_LIMIT -> limit.toString,
            X_RATE_LIMIT_REMAINING -> (limit - counter - 1).toString,
            X_RATE_LIMIT_RESET -> remaining.toString
          )
        }
    }
  }
}