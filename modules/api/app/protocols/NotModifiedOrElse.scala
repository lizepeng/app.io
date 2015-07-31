package protocols

import models.TimeBased
import play.api.http._
import play.api.i18n.Messages
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
object NotModifiedOrElse {

  def apply[T <: TimeBased](block: T => Result)(
    implicit req: RequestHeader, messages: Messages
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