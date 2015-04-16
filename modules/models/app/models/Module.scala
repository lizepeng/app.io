package models

import com.websudos.phantom.CassandraTable
import helpers.ModuleLike

/**
 * @author zepeng.li@gmail.com
 */
trait Module[T <: CassandraTable[T, R], R] extends ModuleLike {
  self: CassandraTable[T, R] =>

  override def moduleName = tableName
}