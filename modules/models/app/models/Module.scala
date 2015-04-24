package models

import com.websudos.phantom.CassandraTable
import helpers.ModuleLike

/**
 * @author zepeng.li@gmail.com
 */
trait Module[R] extends ModuleLike {
  self: CassandraTable[_, R] =>

  override def moduleName = tableName
}