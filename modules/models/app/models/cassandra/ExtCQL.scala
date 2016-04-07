package models.cassandra

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.batch.BatchQuery
import com.websudos.phantom.builder._
import com.websudos.phantom.builder.query._
import helpers.Logging
import play.api.Configuration
import shapeless.HList

/**
 * @author zepeng.li@gmail.com
 */
trait ExtCQL[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] with Logging =>

  def configuration: Configuration

  lazy val flags: Int = configuration
    .getStringSeq("cassandra.cql.log")
    .getOrElse(Seq.empty).map {
    case "create" => 0x20
    case "batch"  => 0x10
    case "select" => 0x08
    case "insert" => 0x04
    case "update" => 0x02
    case "delete" => 0x01
  }.foldLeft(0)(_ | _)

  def CQL(
    cql: CreateQuery.Default[T, R]
  ) = trace(cql, cql.queryString, (flags & 0x20) != 0)

  def CQL[
    Status <: ConsistencyBound](
    cql: BatchQuery[Status]
  ) = trace(cql, cql.queryString, (flags & 0x10) != 0)

  def CQL[
    T1 <: CassandraTable[T1, _],
    R1,
    L <: LimitBound,
    O <: OrderBound,
    S <: ConsistencyBound,
    C <: WhereBound,
    P <: HList](
    cql: SelectQuery[T1, R1, L, O, S, C, P]
  ) = trace(cql, cql.queryString, (flags & 0x08) != 0)

  def CQL[
    T1 <: CassandraTable[T1, _],
    R1,
    S <: ConsistencyBound,
    P <: HList](
    cql: InsertQuery[T1, R1, S, P]
  ) = trace(cql, cql.queryString, (flags & 0x04) != 0)

  def CQL[
    T1 <: CassandraTable[T1, _],
    R1,
    L <: LimitBound,
    O <: OrderBound,
    S <: ConsistencyBound,
    C <: WhereBound,
    P <: HList](
    cql: AssignmentsQuery[T1, R1, L, O, S, C, P]
  ) = trace(cql, cql.queryString, (flags & 0x02) != 0)

  def CQL[
    T1 <: CassandraTable[T1, _],
    R1,
    L <: LimitBound,
    O <: OrderBound,
    S <: ConsistencyBound,
    C <: WhereBound,
    P <: HList](
    cql: ConditionalQuery[T1, R1, L, O, S, C, P]
  ) = trace(cql, cql.queryString, (flags & 0x02) != 0)

  def CQL[
    T1 <: CassandraTable[T1, _],
    R1,
    L <: LimitBound,
    O <: OrderBound,
    S <: ConsistencyBound,
    C <: WhereBound,
    P <: HList](
    cql: DeleteQuery[T1, R1, L, O, S, C, P]
  ) = trace(cql, cql.queryString, (flags & 0x01) != 0)

  def trace[A](cql: A, query: String, log: Boolean): A = {
    if (log) Logger.trace(query)
    cql
  }
}