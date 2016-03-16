package models

import java.net.InetAddress

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait IPRateLimitCanonicalNamed extends CanonicalNamed {

  override val basicName = "ip_rate_limits"
}

sealed class IPRateLimitTable
  extends NamedCassandraTable[IPRateLimitTable, InetAddress]
  with IPRateLimitCanonicalNamed {

  object ip
    extends InetAddressColumn(this)
    with PartitionKey[InetAddress]

  object datetime
    extends DateTimeColumn(this)
    with PrimaryKey[DateTime]

  object counter
    extends CounterColumn(this)

  override def fromRow(r: Row): InetAddress = ip(r)
}

object IPRateLimits
  extends IPRateLimitCanonicalNamed

class IPRateLimits(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends IPRateLimitTable
  with ExtCQL[IPRateLimitTable, InetAddress]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def get(
    ip: InetAddress,
    datetime: DateTime
  ): Future[Long] = CQL {
    select(_.counter)
      .where(_.ip eqs ip)
      .and(_.datetime eqs datetime)
  }.one().map(_.getOrElse(0L))

  def inc(
    ip: InetAddress,
    datetime: DateTime,
    delta: Long = 1L
  ): Future[ResultSet] = CQL {
    update
      .where(_.ip eqs ip)
      .and(_.datetime eqs datetime)
      .modify(_.counter += delta)
  }.future()
}