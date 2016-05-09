package models.summary

import com.websudos.phantom.dsl._
import helpers.ExtMap._
import helpers.StringifierConverts._
import helpers._
import models.cassandra._
import org.joda.time._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
abstract class DailySummaryTable[T <: CassandraTable[T, (KEY, Long)], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends NamedCassandraTable[T, (KEY, Long)] {
  self: CanonicalNamed =>

  object id
    extends StringColumn(this)
      with PartitionKey[String]

  object year_month
    extends StringColumn(this)
      with PartitionKey[String]

  object day
    extends IntColumn(this)
      with ClusteringOrder[Int]

  object key
    extends StringColumn(this)
      with ClusteringOrder[String]

  object count
    extends CounterColumn(this)

  implicit def IDStringifier: Stringifier[ID]
  implicit def KEYStringifier: Stringifier[KEY]
  def keys: KEYS

  def fromRow(r: Row) = (KEYStringifier <<(key(r), keys.Unknown)) -> count(r)
}

trait DailySummary[T <: DailySummaryTable[T, ID, KEYS, KEY], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends EntityTable[(KEY, Long)]
    with ExtCQL[T, (KEY, Long)]
    with BasicPlayComponents
    with CassandraComponents
    with BootingProcess
    with Logging {

  self: DailySummaryTable[T, ID, KEYS, KEY] =>

  onStart(CQL(create.ifNotExists).future())

  def byKey: DailySummaryByKey[_, ID, KEYS, KEY]

  def summary(
    id: ID,
    yearMonth: YearMonth
  ): Future[Monthly[Day, Daily[KEY, Long]]] = CQL {
    select(_.day, _.key, _.count)
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs yearMonth.toString)
  }.fetch().map { list =>
    Monthly(
      list.map(t => (Day(t._1), t._2, t._3))
        .groupBy(_._1)
        .mapValuesSafely { t =>
          Daily(t.map(p => p._2 -> p._3).toMap.keyToType[KEY])
        }
    )
  }

  def summary(
    id: ID,
    date: LocalDate
  ): Future[Daily[KEY, Long]] = CQL {
    select(_.key, _.count)
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.day eqs date.getDayOfMonth)
  }.fetch().map { list =>
    Daily(list.map(p => p._1 -> p._2).toMap.keyToType[KEY])
  }

  def save(id: ID, date: LocalDate, key: KEYS => KEY): Future[ResultSet] =
    for {
      _ <- byKey.save(id, date, key)
      r <- cql_save(id, date, key).future()
    } yield r

  def save(id: ID, date: LocalDate)(counts: Counts[KEY, _]): Future[ResultSet] = {
    (Batch.unlogged /: counts.self) { case (batch, (m, c)) =>
      batch
        .add(cql_save(id, date, _ => m, c))
        .add(byKey.cql_save(id, date, _ => m, c))
    }.future()
  }

  def cql_save(id: ID, date: LocalDate, key: KEYS => KEY, count: Int = 1) = CQL {
    update
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.key eqs (key(keys) >>: KEYStringifier))
      .and(_.day eqs date.getDayOfMonth)
      .modify(_.count += count)
  }

  override def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}

abstract class DailySummaryByKeyTable[T <: CassandraTable[T, (KEY, Long)], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends NamedCassandraTable[T, (KEY, Long)] {
  self: CanonicalNamed =>

  object id
    extends StringColumn(this)
      with PartitionKey[String]

  object year_month
    extends StringColumn(this)
      with PartitionKey[String]

  object key
    extends StringColumn(this)
      with ClusteringOrder[String]

  object day
    extends IntColumn(this)
      with ClusteringOrder[Int]

  object count
    extends CounterColumn(this)

  def IDStringifier: Stringifier[ID]
  def KEYStringifier: Stringifier[KEY]
  def keys: KEYS

  def fromRow(r: Row) = (KEYStringifier <<(key(r), keys.Unknown)) -> count(r)
}

trait DailySummaryByKey[T <: DailySummaryByKeyTable[T, ID, KEYS, KEY], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends EntityTable[(KEY, Long)]
    with ExtCQL[T, (KEY, Long)]
    with BasicPlayComponents
    with CassandraComponents
    with BootingProcess
    with Logging {

  self: DailySummaryByKeyTable[T, ID, KEYS, KEY] =>

  onStart(CQL(create.ifNotExists).future())

  def summary(
    id: ID,
    yearMonth: YearMonth,
    key: KEYS => KEY
  ): Future[Monthly[Day, Long]] = CQL {
    select(_.day, _.count)
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs yearMonth.toString)
      .and(_.key eqs key(keys) >>: KEYStringifier)
  }.fetch().map { list =>
    Monthly(
      list.map(t => Day(t._1) -> t._2).toMap
    )
  }

  def save(id: ID, date: LocalDate, key: KEYS => KEY): Future[ResultSet] = {
    cql_save(id, date, key).future()
  }

  def cql_save(id: ID, date: LocalDate, key: KEYS => KEY, count: Int = 1) = CQL {
    update
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.day eqs date.getDayOfMonth)
      .and(_.key eqs (key(keys) >>: KEYStringifier))
      .modify(_.count += count)
  }

  override def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}