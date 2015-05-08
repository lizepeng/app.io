package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.cassandra._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
sealed class RateLimits
  extends CassandraTable[RateLimits, UUID]
  with ExtCQL[RateLimits, UUID]
  with Logging {

  override val tableName = "rate_limit"

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object key
    extends StringColumn(this)
    with PrimaryKey[String]

  object value
    extends CounterColumn(this)

  override def fromRow(r: Row): UUID = user_id(r)
}

object RateLimit extends RateLimits with Cassandra {

  def get(key: String)(
    implicit user: User
  ): Future[Long] = CQL {
    select(_.value)
      .where(_.user_id eqs user.id)
      .and(_.key eqs key)
  }.one().map(_.getOrElse(0L))

  def inc(key: String, value: Long = 1L)(
    implicit user: User
  ): Future[ResultSet] = CQL {
    update
      .where(_.user_id eqs user.id)
      .and(_.key eqs key)
      .modify(_.value increment value)
  }.future()

  def inc_get(key: String, value: Long = 1L)(
    implicit user: User
  ): Future[Long] = inc(key, value).flatMap(_ => get(key))
}