package protocols

import models.TimeBased
import play.api.http._
import play.api.mvc._

import scala.concurrent._

/**
 * See RFC 7234
 *
 * @author zepeng.li@gmail.com
 */
object HttpCaching {

  def async[T <: TimeBased](block: T => Future[Result])(
    implicit req: RequestHeader, ec: ExecutionContext
  ): T => Future[Result] = { entity =>
    val updated_at = entity.updated_at.withMillisOfSecond(0)
    req.headers.get(HeaderNames.IF_MODIFIED_SINCE)
      .map(dateFormat.parseDateTime) match {
      case Some(d) if !d.isBefore(updated_at) =>
        Future.successful(Results.NotModified)
      case _                                  =>
        block(entity).map(
          _.withHeaders(
            HeaderNames.LAST_MODIFIED -> dateFormat.print(updated_at)
          )
        )
    }
  }

  def apply[T <: TimeBased](block: T => Result)(
    implicit req: RequestHeader, ec: ExecutionContext
  ): T => Future[Result] = async[T](t => Future.successful(block(t)))
}