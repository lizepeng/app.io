package models

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
trait PeriodicallyCleanedCNamed extends CanonicalNamed {

  override val basicName = "periodically_cleaned"
}

sealed class PeriodicallyCleanedTable
  extends NamedCassandraTable[PeriodicallyCleanedTable, UUID]
    with PeriodicallyCleanedCNamed {

  object id extends UUIDColumn(this)
    with PartitionKey[UUID]

  object key extends StringColumn(this)
    with PrimaryKey[String]

  object next_run_time_monthly
    extends OptionalDateTimeColumn(this)

  object next_run_time_weekly
    extends OptionalDateTimeColumn(this)

  object next_run_time_daily
    extends OptionalDateTimeColumn(this)

  object next_run_time_hourly
    extends OptionalDateTimeColumn(this)

  override def fromRow(r: Row): UUID = id(r)
}

object PeriodicallyCleaned extends PeriodicallyCleanedCNamed

class PeriodicallyCleaned(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
) extends PeriodicallyCleanedTable
  with ExtCQL[PeriodicallyCleanedTable, UUID]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def isScheduledMonthly(id: UUID, key: String): Future[Boolean] = CQL {
    select(_.next_run_time_monthly)
      .where(_.id eqs id)
      .and(_.key eqs key)
  }.one().map {_.flatten.isDefined}

  def scheduleMonthly(id: UUID, key: String): Future[FiniteDuration] = set(
    id, key, _.next_run_time_monthly, _.dayOfMonth()
      .withMaximumValue()
      .millisOfDay()
      .withMaximumValue()
  )

  def isScheduledWeekly(id: UUID, key: String): Future[Boolean] = CQL {
    select(_.next_run_time_weekly)
      .where(_.id eqs id)
      .and(_.key eqs key)
  }.one().map {_.flatten.isDefined}

  def scheduleWeekly(id: UUID, key: String): Future[FiniteDuration] = set(
    id, key, _.next_run_time_weekly, _.dayOfWeek()
      .withMaximumValue()
      .millisOfDay()
      .withMaximumValue()
  )

  def isScheduledDaily(id: UUID, key: String): Future[Boolean] = CQL {
    select(_.next_run_time_daily)
      .where(_.id eqs id)
      .and(_.key eqs key)
  }.one().map {_.flatten.isDefined}

  def scheduleDaily(id: UUID, key: String): Future[FiniteDuration] = set(
    id, key, _.next_run_time_daily, _.plusDays(1)
      .millisOfDay()
      .withMaximumValue()
  )

  def isScheduledHourly(id: UUID, key: String): Future[Boolean] = CQL {
    select(_.next_run_time_hourly)
      .where(_.id eqs id)
      .and(_.key eqs key)
  }.one().map {_.flatten.isDefined}

  def scheduleHourly(id: UUID, key: String): Future[FiniteDuration] = set(
    id, key, _.next_run_time_hourly, _.plusHours(1)
      .withMinuteOfHour(0)
      .withSecondOfMinute(0)
      .withMillisOfSecond(0)
  )

  private def set(
    id: UUID,
    key: String,
    column: PeriodicallyCleanedTable => OptionalColumn[_, _, DateTime],
    calcNext: DateTime => DateTime
  ): Future[FiniteDuration] = {
    val now = DateTime.now
    val next = calcNext(now)
    val ttl = new Interval(now, next).toDuration.getStandardSeconds
    CQL {
      insert
        .value(_.id, id)
        .value(_.key, key)
        .value(column, Some(next))
        .ttl(ttl)
    }.future().map(_ => ttl.seconds)
  }
}