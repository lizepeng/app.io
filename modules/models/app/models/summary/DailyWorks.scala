package models.summary

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers.ExtEnumeratee.Enumeratee
import helpers._
import models.cassandra._
import models.misc._
import org.joda.time._
import play.api.libs.iteratee.Enumerator

import scala.concurrent.Future
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
abstract class DailyWorksTable[T <: CassandraTable[T, (KEY, Set[UUID])], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends NamedCassandraTable[T, (KEY, Set[UUID])] {
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

  object work_list
    extends ListColumn[T, (KEY, Set[UUID]), UUID](this)

  object work_set
    extends SetColumn[T, (KEY, Set[UUID]), UUID](this)

  def IDStringifier: Stringifier[ID]
  def KEYStringifier: Stringifier[KEY]
  def keys: KEYS

  def fromRow(r: Row) = (KEYStringifier <<(key(r), keys.Unknown)) -> work_set(r)
}

abstract class DailyWorksByKeyTable[T <: CassandraTable[T, (KEY, Set[UUID])], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends NamedCassandraTable[T, (KEY, Set[UUID])] {
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

  object work_list
    extends ListColumn[T, (KEY, Set[UUID]), UUID](this)

  object work_set
    extends SetColumn[T, (KEY, Set[UUID]), UUID](this)

  def IDStringifier: Stringifier[ID]
  def KEYStringifier: Stringifier[KEY]
  def keys: KEYS

  def fromRow(r: Row) = (KEYStringifier <<(key(r), keys.Unknown)) -> work_set(r)
}

trait DailyWorks[T <: DailyWorksTable[T, ID, KEYS, KEY], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends EntityTable[(KEY, Set[UUID])]
    with ExtCQL[T, (KEY, Set[UUID])]
    with BasicPlayComponents
    with CassandraComponents
    with BootingProcess
    with Logging {

  self: DailyWorksTable[T, ID, KEYS, KEY] =>

  onStart(CQL(create.ifNotExists).future())

  def byKey: DailyWorksByKey[_, ID, KEYS, KEY]
  def daily: DailySummary[_, ID, KEYS, KEY]
  def monthly: MonthlySummary[_, ID, KEYS, KEY]
  def yearly: YearlySummary[_, ID, KEYS, KEY]

  def find(
    id: ID,
    date: LocalDate,
    key: KEYS => KEY
  ): Future[Set[UUID]] = CQL {
    select
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.day eqs date.getDayOfMonth)
      .and(_.key eqs key(keys) >>: KEYStringifier)
  }.one().map {
    case None    => Set.empty
    case Some(o) => o._2
  }

  def find(
    id: ID,
    date: LocalDate
  ): Future[Daily[KEY, Set[UUID]]] = CQL {
    select
      .where(_.id eqs (id >>: IDStringifier))
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.day eqs date.getDayOfMonth)
  }.fetch().map { list =>
    Daily(list.toMap)
  }

  def save(
    id: ID,
    key: KEYS => KEY,
    work_id: UUID,
    date: LocalDate
  ): Future[ResultSet] = CQL {
    Batch.logged
      .add(byKey.cql_save(id, date, key, work_id))
      .add(cql_save(id, date, key, work_id))
  }.future.andThen {
    case Success(_) =>
      daily.save(id, date, key)
      monthly.save(id, date, key)
      yearly.save(id, date, key)
  }

  def save(
    id: ID,
    work_id: UUID,
    date: LocalDate
  )(
    counts: Counts[KEY, _]
  ): Future[ResultSet] = {
    (Batch.unlogged /: counts.self) { case (batch, (m, _)) =>
      batch
        .add(cql_save(id, date, _ => m, work_id))
        .add(byKey.cql_save(id, date, _ => m, work_id))
    }.future()
  }

  def cql_save(
    id: ID,
    date: LocalDate,
    key: KEYS => KEY,
    work_id: UUID
  ) = {
    update
      .where(_.id eqs (id >>: IDStringifier))
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.day eqs date.getDayOfMonth)
      .and(_.key eqs (key(keys) >>: KEYStringifier))
      .modify(_.work_list prepend work_id)
      .and(_.work_set add work_id)
  }

  override def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}

trait DailyWorksByKey[T <: DailyWorksByKeyTable[T, ID, KEYS, KEY], ID, KEYS <: EnumLike.Definition[KEY], KEY <: EnumLike.Value]
  extends EntityTable[(KEY, Set[UUID])]
    with ExtCQL[T, (KEY, Set[UUID])]
    with BasicPlayComponents
    with CassandraComponents
    with BootingProcess
    with Logging {

  self: DailyWorksByKeyTable[T, ID, KEYS, KEY] =>

  onStart(CQL(create.ifNotExists).future())

  /**
   * 返回x年x月的某种工作中的一页
   */
  def set(
    id: ID,
    pager: Pager,
    year_month: YearMonth
  )(key: KEYS => KEY) = Page(pager)(streamSet(id, year_month)(key))

  /**
   * 返回x年x月~x年x月的某种工作中的一页
   */
  def set(
    id: ID,
    pager: Pager,
    start: YearMonth,
    end: YearMonth
  )(key: KEYS => KEY) = Page(pager)(streamSet(id, start, end)(key))

  /**
   * 返回最近三个月的某种工作中的一页
   */
  def set(
    id: ID,
    pager: Pager
  )(key: KEYS => KEY) = Page(pager)(streamSet(id)(key))

  /**
   * 返回x年的某种工作中的一页
   */
  def set(
    id: ID,
    pager: Pager,
    year: Year
  )(key: KEYS => KEY) = Page(pager)(streamSet(id, year)(key))

  /**
   * 返回x年x月的某种工作
   */
  def streamSet(
    id: ID,
    year_month: YearMonth
  )(key: KEYS => KEY): Enumerator[UUID] = {
    CQL {
      select(_.work_set)
        .where(_.id eqs id >>: IDStringifier)
        .and(_.year_month eqs year_month.toString)
        .and(_.key eqs key(keys) >>: KEYStringifier)
    }.fetchEnumerator &>
      Enumeratee.mapFlatten { set => Enumerator(set.toSeq: _*) }
  }

  /**
   * 返回最近三个月的某种工作
   */
  def streamSet(
    id: ID
  )(key: KEYS => KEY): Enumerator[UUID] = {
    Enumerator((0 to 2).map(YearMonth.now.minusMonths): _*) &>
      Enumeratee.mapFlatten(streamSet(id, _)(key))
  }

  /**
   * 返回x年的某种工作
   */
  def streamSet(
    id: ID,
    year: Int
  )(key: KEYS => KEY): Enumerator[UUID] = {
    Enumerator((1 to 12).map(new YearMonth(year, _)): _*) &>
      Enumeratee.mapFlatten(streamSet(id, _)(key))
  }

  /**
   * 返回x年x月~x年x月的某种工作
   */
  def streamSet(
    id: ID,
    start: YearMonth,
    end: YearMonth
  )(key: KEYS => KEY): Enumerator[UUID] = {
    Enumerator.unfold(start) { current =>
      if (current isAfter end) None
      else Some(current.plusMonths(1), current)
    } &> Enumeratee.mapFlatten(streamSet(id, _)(key))
  }

  ////////////////////////////////////////////////////////////////////////

  /**
   * 返回x年x月的某种工作中的一页
   */
  def list(
    id: ID,
    pager: Pager,
    year_month: YearMonth
  )(key: KEYS => KEY) = Page(pager)(streamList(id, year_month)(key))


  /**
   * 返回最近三个月的某种工作中的一页
   */
  def list(
    id: ID,
    pager: Pager
  )(key: KEYS => KEY) = Page(pager)(streamList(id)(key))

  /**
   * 返回x年的某种工作中的一页
   */
  def list(
    id: ID,
    pager: Pager,
    year: Year
  )(key: KEYS => KEY) = Page(pager)(streamList(id, year)(key))

  /**
   * 返回x年x月~x年x月的某种工作中的一页
   */
  def list(
    id: ID,
    pager: Pager,
    start: YearMonth,
    end: YearMonth
  )(key: KEYS => KEY) = Page(pager)(streamList(id, start, end)(key))

  /**
   * 返回x年x月的某种工作
   */
  def streamList(
    id: ID,
    year_month: YearMonth
  )(key: KEYS => KEY): Enumerator[UUID] = {
    CQL {
      select(_.work_list)
        .where(_.id eqs id >>: IDStringifier)
        .and(_.year_month eqs year_month.toString)
        .and(_.key eqs key(keys) >>: KEYStringifier)
    }.fetchEnumerator &>
      Enumeratee.mapFlatten { list => Enumerator(list: _*) }
  }

  /**
   * 返回最近三个月的某种工作
   */
  def streamList(
    id: ID
  )(key: KEYS => KEY): Enumerator[UUID] = {
    Enumerator((0 to 2).map(YearMonth.now.minusMonths): _*) &>
      Enumeratee.mapFlatten(streamList(id, _)(key))
  }

  /**
   * 返回x年的某种工作
   */
  def streamList(
    id: ID,
    year: Int
  )(key: KEYS => KEY): Enumerator[UUID] = {
    Enumerator((1 to 12).map(new YearMonth(year, _)): _*) &>
      Enumeratee.mapFlatten(streamList(id, _)(key))
  }

  /**
   * 返回x年x月~x年x月的某种工作
   */
  def streamList(
    id: ID,
    start: YearMonth,
    end: YearMonth
  )(key: KEYS => KEY): Enumerator[UUID] = {
    Enumerator.unfold(start) { current =>
      if (current isAfter end) None
      else Some(current.plusMonths(1), current)
    } &> Enumeratee.mapFlatten(streamList(id, _)(key))
  }

  def cql_save(
    id: ID,
    date: LocalDate,
    key: KEYS => KEY,
    work_id: UUID
  ) = {
    update
      .where(_.id eqs id >>: IDStringifier)
      .and(_.year_month eqs new YearMonth(date).toString)
      .and(_.key eqs (key(keys) >>: KEYStringifier))
      .and(_.day eqs date.getDayOfMonth)
      .modify(_.work_list prepend work_id)
      .and(_.work_set add work_id)
  }

  override def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}