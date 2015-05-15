package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import org.joda.time.DateTime
import play.api.libs.json.Reads

/**
 * @author zepeng.li@gmail.com
 */
trait HasID[T] {

  def id: T
}

trait TimeBased {

  def created_at: DateTime

  def updated_at: DateTime
}

trait HasUUID extends HasID[UUID] with TimeBased {

  def id: UUID

  lazy val created_at = TimeBased.extractDatetime(id)
}

trait JsonReadable[T] {

  def reads: Reads[T]
}

object TimeBased {

  def extractDatetime(uuid: UUID): DateTime = {
    new DateTime(UUIDs.unixTimestamp(uuid))
  }
}