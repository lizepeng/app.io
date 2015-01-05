package models.cfs

import java.util.UUID

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.column.TimeUUIDColumn

/**
 * @author zepeng.li@gmail.com
 */

trait INodesKey[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  override val tableName = "inodes"

  object inode_id
    extends TimeUUIDColumn(this)
    with PartitionKey[UUID]

  object offset
    extends LongColumn(this)
    with ClusteringOrder[Long] with Ascending

}

trait INodesStatic[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object name
    extends StringColumn(self)
    with StaticColumn[String]

  object parent
    extends UUIDColumn(self)
    with StaticColumn[UUID]

  object is_directory
    extends BooleanColumn(self)
    with StaticColumn[Boolean]

  object size
    extends LongColumn(self)
    with StaticColumn[Long]

  object indirect_block_size
    extends IntColumn(self)
    with StaticColumn[Int]

  object block_size
    extends IntColumn(self)
    with StaticColumn[Int]

  object attributes
    extends MapColumn[T, R, String, String](self)
    with StaticColumn[Map[String, String]]

}

trait INodesDynamic[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object length extends LongColumn(self)

  object indirect_block_id extends TimeUUIDColumn(self)
}