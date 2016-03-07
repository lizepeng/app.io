package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait RateLimitCanonicalNamed extends CanonicalNamed {

  override val basicName = "rate_limits"
}

sealed class RateLimitTable
  extends NamedCassandraTable[RateLimitTable, UUID]
  with RateLimitCanonicalNamed {

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object resource
    extends StringColumn(this)
    with PrimaryKey[String]

  object datetime
    extends DateTimeColumn(this)
    with PrimaryKey[DateTime]

  object counter
    extends CounterColumn(this)

  override def fromRow(r: Row): UUID = user_id(r)
}

object RateLimits
  extends RateLimitCanonicalNamed

class RateLimits(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends RateLimitTable
  with ExtCQL[RateLimitTable, UUID]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def get(
    resource: String,
    datetime: DateTime
  )(
    implicit user: User
  ): Future[Long] = CQL {
    select(_.counter)
      .where(_.user_id eqs user.id)
      .and(_.resource eqs resource)
      .and(_.datetime eqs datetime)
  }.one().map(_.getOrElse(0L))

  def inc(
    resource: String,
    datetime: DateTime,
    delta: Long = 1L
  )(
    implicit user: User
  ): Future[ResultSet] = CQL {
    update
      .where(_.user_id eqs user.id)
      .and(_.resource eqs resource)
      .and(_.datetime eqs datetime)
      .modify(_.counter += delta)
  }.future()
}