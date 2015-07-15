package models.cassandra

import com.websudos.phantom.dsl._
import helpers.CanonicalNamed

/**
 * @author zepeng.li@gmail.com
 */
abstract class NamedCassandraTable[T <: CassandraTable[T, R], R]
  extends CassandraTable[T, R] {
  self: CanonicalNamed =>

  override def tableName = basicName
}