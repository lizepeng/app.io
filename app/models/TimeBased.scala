package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import org.joda.time.DateTime

/**
 * @author zepeng.li@gmail.com
 */
trait TimeBased {
  val id: UUID

  lazy val created_on = new DateTime(UUIDs.unixTimestamp(id))
}
