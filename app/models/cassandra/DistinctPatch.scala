package models.cassandra

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.column.SelectColumn
import com.websudos.phantom.query.SelectQuery

/**
 * @author zepeng.li@gmail.com
 */
trait DistinctPatch[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  override def distinct[A](f1: T => SelectColumn[A]): SelectQuery[T, A] = {
    val t = this.asInstanceOf[T]
    val c = f1(t)
    new SelectQuery[T, A](t, QueryBuilder.select.distinct().column(c.col.name).from(tableName), c.apply)
  }
}