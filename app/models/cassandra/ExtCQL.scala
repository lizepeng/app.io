package models.cassandra

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.column.SelectColumn
import com.websudos.phantom.query._
import play.api.LoggerLike

/**
 * @author zepeng.li@gmail.com
 */
trait ExtCQL[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  lazy val flags: Int = play.api.Play.current
    .configuration
    .getStringSeq("cassandra.cql.log")
    .getOrElse(Seq.empty).map {
    case "batch"  => 0x10
    case "select" => 0x08
    case "insert" => 0x04
    case "update" => 0x02
    case "delete" => 0x01
  }.foldLeft(0)(_ | _)

  def Logger: LoggerLike

  override def distinct[A](f1: T => SelectColumn[A]): SelectQuery[T, A] = {
    val t = this.asInstanceOf[T]
    val c = f1(t)
    new SelectQuery[T, A](t, QueryBuilder.select.distinct().column(c.col.name).from(tableName), c.apply)
  }

  def distinct[A, B](f1: T => SelectColumn[A], f2: T => SelectColumn[B]): SelectQuery[T, (A, B)] = {
    val t = this.asInstanceOf[T]
    val c1 = f1(t)
    val c2 = f2(t)
    new SelectQuery[T, (A, B)](t, QueryBuilder.select.distinct().column(c1.col.name).column(c2.col.name).from(tableName), r => (c1(r), c2(r)))
  }

  def CQL(cql: BatchStatement) =
    trace(cql, cql.queryString, (flags & 0x10) != 0)

  def CQL[A](cql: SelectQuery[T, A]) =
    trace(cql, cql.queryString, (flags & 0x08) != 0)

  def CQL[A](cql: SelectWhere[T, A]) =
    trace(cql, cql.queryString, (flags & 0x08) != 0)

  def CQL(cql: InsertQuery[T, R]) =
    trace(cql, cql.queryString, (flags & 0x04) != 0)

  def CQL(cql: AssignmentsQuery[T, R]) =
    trace(cql, cql.queryString, (flags & 0x02) != 0)

  def CQL(cql: ConditionalUpdateQuery[T, R]) =
    trace(cql, cql.queryString, (flags & 0x02) != 0)

  def CQL(cql: DeleteWhere[T, R]) =
    trace(cql, cql.queryString, (flags & 0x01) != 0)

  def trace[A](cql: A, query: String, log: Boolean): A = {
    if (log) Logger.trace(query)
    cql
  }

}