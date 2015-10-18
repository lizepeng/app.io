package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers._
import helpers.syntax._
import models.cassandra._
import models.cfs.Block.BLK
import models.cfs.FileSystem._
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class File(
  name: String,
  path: Path,
  owner_id: UUID,
  parent: UUID,
  id: UUID = UUIDs.timeBased(),
  size: Long = 0,
  indirect_block_size: Int = 1024 * 32 * 1024 * 8,
  block_size: Int = 1024 * 8,
  permission: Permission = Role.owner.rw,
  ext_permission: Map[UUID, Access] = Map(),
  attributes: Map[String, String] = Map(),
  is_directory: Boolean = false
) extends INode {

  def read(offset: Long = 0)(
    implicit cfs: CassandraFileSystem
  ): Enumerator[BLK] =
    if (offset == 0) cfs._indirectBlocks.read(id)
    else cfs._indirectBlocks.read(this, offset)

  def save()(
    implicit cfs: CassandraFileSystem
  ): Iteratee[BLK, File] =
    cfs._files.streamWriter(this)

  override def purge()(
    implicit cfs: CassandraFileSystem
  ) = {
    super.purge().andThen { case _ => cfs._files.purge(id) }
  }
}

/**
 *
 */
sealed class FileTable
  extends NamedCassandraTable[FileTable, File]
  with INodeCanonicalNamed
  with INodeKey[FileTable, File]
  with INodeColumns[FileTable, File]
  with FileColumns[FileTable, File] {

  override def fromRow(r: Row): File = {
    File(
      "",
      Path(),
      owner_id(r),
      parent(r),
      inode_id(r),
      size(r),
      indirect_block_size(r),
      block_size(r),
      Permission(permission(r)),
      ext_permission(r).mapValues(Access(_)),
      attributes(r)
    )
  }
}

object File extends CanonicalNamed with ExceptionDefining {

  override val basicName: String = "file"

  case class NotFound(id: UUID)
    extends BaseException(error_code("not.found"))

  implicit val jsonWrites = new Writes[File] {
    override def writes(o: File): JsValue = Json.obj(
      "id" -> o.id,
      "name" -> o.name,
      "path" -> o.path,
      "size" -> o.size,
      "owner_id" -> o.owner_id,
      "created_at" -> o.created_at,
      "is_directory" -> o.is_directory,
      "is_file" -> !o.is_directory
    )
  }
}

class Files(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder,
  val _blocks: Blocks,
  val _indirectBlocks: IndirectBlocks
)
  extends FileTable
  with ExtCQL[FileTable, File]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  def find(id: UUID)(
    implicit onFound: File => File
  ): Future[File] = CQL {
    select.where(_.inode_id eqs id)
  }.one().map {
    case None    => throw File.NotFound(id)
    case Some(f) => onFound(f)
  }

  def streamWriter(inode: File): Iteratee[BLK, File] = {
    import scala.Predef._
    Enumeratee.grouped[BLK] {
      Traversable.take[BLK](inode.block_size) &>>
        Iteratee.consume[BLK]()
    } &>>
      Iteratee.foldM[BLK, IndirectBlock](new IndirectBlock(inode.id)) {
        (curr, blk) =>
          _blocks.write(curr.id, curr.length, blk)
          val next = curr + blk.length

          next.length < inode.indirect_block_size match {
            case true  => Future.successful(next)
            case false => _indirectBlocks.write(next).map(_.next)
          }
      }.mapM { last =>
        _indirectBlocks.write(last) iff (last.length != 0)
        this.write(inode.copy(size = last.offset + last.length))
      }

  }

  def purge(id: UUID): Future[ResultSet] = for {
    _ <- _indirectBlocks.purge(id)
    u <- CQL {delete.where(_.inode_id eqs id)}.future()
  } yield u

  private def write(f: File): Future[File] = CQL {
    insert.value(_.inode_id, f.id)
      .value(_.parent, f.parent)
      .value(_.is_directory, false)
      .value(_.size, f.size)
      .value(_.indirect_block_size, f.indirect_block_size)
      .value(_.block_size, f.block_size)
      .value(_.owner_id, f.owner_id)
      .value(_.permission, f.permission.self)
      .value(_.ext_permission, f.ext_permission.mapValues(_.self))
      .value(_.attributes, f.attributes)
  }.future().map(_ => f)
}