package models.cassandra

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.websudos.phantom.CassandraTable
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
    case "select" => 8
    case "insert" => 4
    case "update" => 2
    case "delete" => 1
  }.foldLeft(0)(_ | _)

  def Logger: LoggerLike

  override def distinct[A](f1: T => SelectColumn[A]): SelectQuery[T, A] = {
    val t = this.asInstanceOf[T]
    val c = f1(t)
    new SelectQuery[T, A](t, QueryBuilder.select.distinct().column(c.col.name).from(tableName), c.apply)
  }

  def CQL[A](cql: SelectQuery[T, A]) =
    trace(cql, cql.queryString, (flags & 8) != 0)

  def CQL[A](cql: SelectWhere[T, A]) =
    trace(cql, cql.queryString, (flags & 8) != 0)

  def CQL(cql: InsertQuery[T, R]) =
    trace(cql, cql.queryString, (flags & 4) != 0)

  def CQL(cql: AssignmentsQuery[T, R]) =
    trace(cql, cql.queryString, (flags & 2) != 0)

  def CQL(cql: DeleteWhere[T, R]) =
    trace(cql, cql.queryString, (flags & 1) != 0)

  def trace[A](cql: A, query: String, log: Boolean): A = {
    if (log) Logger.trace(query)
    cql
  }
}