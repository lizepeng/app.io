package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import org.joda.time._

/**
 * @author zepeng.li@gmail.com
 */
trait HasID[T] {

  def id: T
}

trait TimeBased {

  def updated_at: DateTime
}

trait HasUUID extends HasID[UUID] {

  def id: UUID

  lazy val created_at: DateTime  = TimeBased.extractDatetime(id)
  lazy val year_month: YearMonth = TimeBased.extractYearMonth(id)
}

object TimeBased {

  def extractDatetime(uuid: UUID): DateTime = {
    new DateTime(UUIDs.unixTimestamp(uuid))
  }

  def extractYearMonth(uuid: UUID): YearMonth = {
    new YearMonth(extractDatetime(uuid))
  }
}