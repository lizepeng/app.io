package models

import com.websudos.phantom.CassandraTable
import helpers.CanonicalNamed

/**
 * @author zepeng.li@gmail.com
 */

trait CanonicalNamedModel[R] extends CanonicalNamed {
  self: CassandraTable[_, R] =>

  override lazy val basicName = tableName
}

trait CanonicalModel[R] extends CanonicalNamed


trait CassandraTableName[R] {
  self: CanonicalNamed with CassandraTable[_, R] =>

  override lazy val tableName = basicName
}