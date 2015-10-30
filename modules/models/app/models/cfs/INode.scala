package models.cfs

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.cfs.CassandraFileSystem._
import models.{HasUUID, TimeBased}
import org.joda.time.DateTime
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait INode extends HasUUID with TimeBased {

  def name: String

  def parent: UUID

  def is_directory: Boolean

  def owner_id: UUID

  def permission: Permission

  def ext_permission: Map[UUID, Permission]

  def attributes: Map[String, String]

  lazy val updated_at: DateTime = created_at

  def rename(newName: String)(
    implicit cfs: CassandraFileSystem
  ): Future[Boolean] = {
    for {
      pdir <- cfs._directories.find(parent)(onFound = p => p)
      done <- cfs._directories.renameChild(pdir, this, newName)
    } yield done
  }

  def delete()(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] =
    for {
      pdir <- cfs._directories.find(parent)(onFound = p => p)
      done <- cfs._directories.delChild(pdir, this.name)
    } yield Unit

}

trait INodeCanonicalNamed extends CanonicalNamed {

  override val basicName = "inodes"
}

trait INodeKey[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object inode_id
    extends TimeUUIDColumn(self)
    with PartitionKey[UUID]

}

trait INodeColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object parent
    extends UUIDColumn(self)
    with StaticColumn[UUID]

  object is_directory
    extends BooleanColumn(self)
    with StaticColumn[Boolean]

  object owner_id
    extends UUIDColumn(self)
    with StaticColumn[UUID]

  object permission
    extends LongColumn(self)
    with StaticColumn[Long]

  object ext_permission
    extends StaticMapColumn[T, R, UUID, Int](self)
    with StaticColumn[Map[UUID, Int]]

  object attributes
    extends StaticMapColumn[T, R, String, String](self)
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
sealed class INodeTable
  extends NamedCassandraTable[INodeTable, Row]
  with INodeCanonicalNamed
  with INodeKey[INodeTable, Row]
  with INodeColumns[INodeTable, Row]
  with FileColumns[INodeTable, Row]
  with DirectoryColumns[INodeTable, Row] {

  override def fromRow(r: Row): Row = r
}

object INode extends INodeCanonicalNamed {

  implicit val jsonWrites = new Writes[INode] {
    override def writes(o: INode): JsValue = o match {
      case d: Directory => Json.toJson(d)
      case f: File      => Json.toJson(f)
    }
  }
}

class INodes(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends INodeTable
  with ExtCQL[INodeTable, Row]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  create.ifNotExists.future()

  def find(id: UUID): Future[Option[Row]] = {
    CQL {select.where(_.inode_id eqs id)}.one()
  }
}