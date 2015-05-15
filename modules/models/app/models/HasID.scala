package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import helpers.ModuleLike
import org.joda.time.DateTime
import play.api.libs.json._

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

  def always(default: => T) = new Reads[T] {
    def reads(json: JsValue) = JsSuccess(default)
  }
}

trait Module[R] extends ModuleLike {
  self: CassandraTable[_, R] =>

  override def moduleName = tableName
}

object TimeBased {

  def extractDatetime(uuid: UUID): DateTime = {
    new DateTime(UUIDs.unixTimestamp(uuid))
  }
}