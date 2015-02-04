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

  def Logger: LoggerLike

  override def distinct[A](f1: T => SelectColumn[A]): SelectQuery[T, A] = {
    val t = this.asInstanceOf[T]
    val c = f1(t)
    new SelectQuery[T, A](t, QueryBuilder.select.distinct().column(c.col.name).from(tableName), c.apply)
  }

  def CQL[A](cql: SelectQuery[T, A]) = traceCQL(cql, cql.queryString)

  def CQL[A](cql: SelectWhere[T, A]) = traceCQL(cql, cql.queryString)

  def CQL(cql: InsertQuery[T, R]) = traceCQL(cql, cql.queryString)

  def CQL(cql: AssignmentsQuery[T, R]) = traceCQL(cql, cql.queryString)

  def CQL(cql: DeleteWhere[T, R]) = traceCQL(cql, cql.queryString)

  def traceCQL[A](cql: A, queryString: String): A = {
    Logger.trace(queryString)
    cql
  }
}