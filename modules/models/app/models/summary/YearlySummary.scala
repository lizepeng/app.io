package models.summary

import com.websudos.phantom.dsl._
import helpers.ExtMap._
import helpers.StringifierMapConverts._
import helpers._
import models.cassandra._
import org.joda.time._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
abstract class YearlySummaryTable[T <: CassandraTable[T, (KEY, Long)], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends NamedCassandraTable[T, (KEY, Long)] {
  self: CanonicalNamed =>

  object id
    extends StringColumn(this)
      with PartitionKey[String]

  object year
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

trait YearlySummary[T <: YearlySummaryTable[T, ID, KEYS, KEY], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends EntityTable[(KEY, Long)]
    with ExtCQL[T, (KEY, Long)]
    with BasicPlayComponents
    with CassandraComponents
    with BootingProcess
    with Logging {

  self: YearlySummaryTable[T, ID, KEYS, KEY] =>

  onStart(CQL(create.ifNotExists).future())

  def byKey: YearlySummaryByKey[_, ID, KEYS, KEY]

  def summary(id: ID): Future[Map[Year, Yearly[KEY, Long]]] = CQL {
    select(_.year, _.key, _.count)
      .where(_.id eqs id >>: IDStringifier)
  }.fetch().map {
    _.map(t => (Year(t._1), t._2, t._3))
      .groupBy(_._1)
      .mapValuesSafely { t =>
        Yearly(t.map(p => p._2 -> p._3).toMap.keyToType[KEY])
      }
  }

  def summary(id: ID, year: Year): Future[Yearly[KEY, Long]] = CQL {
    select(_.key, _.count)
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year eqs year)
  }.fetch().map { list =>
    Yearly(list.map(p => p._1 -> p._2).toMap.keyToType[KEY])
  }
  def save(id: ID, date: LocalDate, key: KEYS => KEY): Future[ResultSet] =
    for {
      _ <- byKey.save(id, date, key)
      r <- cql_save(id, date, key).future()
    } yield r

  def cql_save(id: ID, date: LocalDate, key: KEYS => KEY, count: Int = 1) = CQL {
    update
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year eqs date.getYear)
      .and(_.key eqs (key(keys) >>: KEYStringifier))
      .modify(_.count += count)
  }

  override def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}

abstract class YearlySummaryByKeyTable[T <: CassandraTable[T, (KEY, Long)], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends NamedCassandraTable[T, (KEY, Long)] {
  self: CanonicalNamed =>

  object id
    extends StringColumn(this)
      with PartitionKey[String]

  object key
    extends StringColumn(this)
      with ClusteringOrder[String]

  object year
    extends IntColumn(this)
      with ClusteringOrder[Int]

  object count
    extends CounterColumn(this)

  def IDStringifier: Stringifier[ID]
  def KEYStringifier: Stringifier[KEY]
  def keys: KEYS

  def fromRow(r: Row) = (KEYStringifier <<(key(r), keys.Unknown)) -> count(r)
}

trait YearlySummaryByKey[T <: YearlySummaryByKeyTable[T, ID, KEYS, KEY], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends EntityTable[(KEY, Long)]
    with ExtCQL[T, (KEY, Long)]
    with BasicPlayComponents
    with CassandraComponents
    with BootingProcess
    with Logging {

  self: YearlySummaryByKeyTable[T, ID, KEYS, KEY] =>

  onStart(CQL(create.ifNotExists).future())

  def summary(id: ID, key: KEYS => KEY): Future[Map[Year, Long]] = CQL {
    select(_.year, _.count)
      .where(_.id eqs id >>: IDStringifier)
      .and(_.key eqs key(keys) >>: KEYStringifier)
  }.fetch().map {
    _.map(t => Year(t._1) -> t._2).toMap
  }

  def save(id: ID, date: LocalDate, key: KEYS => KEY): Future[ResultSet] = {
    cql_save(id, date, key).future()
  }

  def cql_save(id: ID, date: LocalDate, key: KEYS => KEY, count: Int = 1) = CQL {
    update
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year eqs date.getYear)
      .and(_.key eqs (key(keys) >>: KEYStringifier))
      .modify(_.count += count)
  }

  override def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}