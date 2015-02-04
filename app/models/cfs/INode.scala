package models.cfs

import java.util.UUID

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.TimeBased
import models.cassandra.{Cassandra, ExtCQL}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait INode extends TimeBased {

  def name: String

  def parent: UUID

  def is_directory: Boolean

  def attributes: Map[String, String]
}

trait INodeKey[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object inode_id
    extends TimeUUIDColumn(self)
    with PartitionKey[UUID]

}

trait INodeColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  override val tableName = "inodes"

  object parent
    extends UUIDColumn(self)
    with StaticColumn[UUID]

  object is_directory
    extends BooleanColumn(self)
    with StaticColumn[Boolean]

  object attributes
    extends MapColumn[T, R, String, String](self)
    with StaticColumn[Map[String, String]]

}

trait FileColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object size
    extends LongColumn(self)
    with StaticColumn[Long]

  object indirect_block_size
    extends IntColumn(self)
    with StaticColumn[Int]

  object block_size
    extends IntColumn(self)
    with StaticColumn[Int]

}

trait DirectoryColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object name
    extends StringColumn(self)
    with ClusteringOrder[String] with Ascending

  object child_id
    extends UUIDColumn(self)

}

/**
 *
 */
sealed class INodes
  extends CassandraTable[INodes, INode]
  with INodeKey[INodes, INode]
  with INodeColumns[INodes, INode]
  with FileColumns[INodes, INode]
  with DirectoryColumns[INodes, INode]
  with ExtCQL[INodes, INode]
  with Logging {

  override def fromRow(r: Row): INode = {
    if (!is_directory(r)) File.fromRow(r)
    else Directory.fromRow(r)
  }
}

object INode extends INodes with Cassandra {

  def find(id: UUID): Future[Option[INode]] = {
    CQL {select.where(_.inode_id eqs id)}.one()
  }
}