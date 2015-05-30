package controllers.api

import helpers.{AppConfig, ModuleLike}
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import protocols.JsonProtocol._
import security._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object RateLimit extends ModuleLike with AppConfig {

  override val moduleName = "rate_limit"

  def apply(resource: CheckedResource): RateLimit = new RateLimit(
    config.getInt("limit").getOrElse(50),
    config.getInt("span").getOrElse(15),
    moduleName,
    resource
  )
}

class RateLimit(
  limit: Int,
  span: Int,
  moduleName: String,
  resource: CheckedResource
) extends ActionFunction[UserRequest, UserRequest] with ExHeaders {

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

    models.RateLimit.get(res, period_start)(user)
      .flatMap { counter =>
      if (counter >= limit) Future.successful {
        Results.TooManyRequest {
          JsonMessage(s"api.$moduleName.exceeded")
        }.withHeaders(
            X_RATE_LIMIT_LIMIT -> limit.toString,
            X_RATE_LIMIT_REMAINING -> "0",
            X_RATE_LIMIT_RESET -> remaining.toString
          )
      }
      else
        for {
          ___ <- models.RateLimit.inc(res, period_start)(user)
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