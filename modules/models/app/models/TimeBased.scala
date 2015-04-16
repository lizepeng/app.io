package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import org.joda.time.DateTime

/**
 * @author zepeng.li@gmail.com
 */
trait TimeBased {

  def id: UUID

  lazy val created_at = TimeBased.extractDatetime(id)

}

object TimeBased {

  def extractDatetime(uuid: UUID): DateTime = {
    new DateTime(UUIDs.unixTimestamp(uuid))
  }
}
