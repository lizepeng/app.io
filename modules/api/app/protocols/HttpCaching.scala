package protocols

import models.TimeBased
import play.api.http._
import play.api.mvc._

/**
 * See RFC 7234
 *
 * @author zepeng.li@gmail.com
 */
object HttpCaching {

  def apply[T <: TimeBased](block: T => Result)(
    implicit req: RequestHeader
  ): T => Result = { entity =>
    val updated_at = entity.updated_at.withMillisOfSecond(0)
    req.headers.get(HeaderNames.IF_MODIFIED_SINCE)
      .map(dateFormat.parseDateTime) match {
      case Some(d) if !d.isBefore(updated_at) =>
        Results.NotModified
      case _                                  =>
        block(entity).withHeaders(
          HeaderNames.LAST_MODIFIED -> dateFormat.print(updated_at)
        )
    }
  }
}