package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers._
import helpers.syntax._
import models.CanonicalNamedModel
import models.cassandra.{Cassandra, ExtCQL}
import models.cfs.Block.BLK
import play.api.libs.iteratee._

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
  permission: Long = 6L << 60,
  ext_permission: Map[UUID, Int] = Map(),
  attributes: Map[String, String] = Map(),
  is_directory: Boolean = false
) extends INode {

  def read(offset: Long = 0)(implicit cfs: CFS): Enumerator[BLK] =
    if (offset == 0) cfs.indirectBlocks.read(id)
    else cfs.indirectBlocks.read(this, offset)

  def save()(implicit cfs: CFS): Iteratee[BLK, File] =
    cfs.files.streamWriter(this)

  override def purge()(implicit cfs: CFS) = {
    super.purge().andThen { case _ => cfs.files.purge(id) }
  }
}

/**
 *
 */
sealed class FileTable
  extends CassandraTable[FileTable, File]
  with INodeKey[FileTable, File]
  with INodeColumns[FileTable, File]
  with FileColumns[FileTable, File]
  with CanonicalNamedModel[File]
  with Logging {

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
      permission(r),
      ext_permission(r),
      attributes(r)
    )
  }
}

object File
  extends FileTable
  with ExceptionDefining {

  case class NotFound(id: UUID)
    extends BaseException(error_code("file.not.found"))

}

class Files(
  val basicPlayApi: BasicPlayApi,
  val Block: Blocks,
  val IndirectBlock: IndirectBlocks
)
  extends FileTable
  with ExtCQL[FileTable, File]
  with BasicPlayComponents
  with Cassandra {

  import File._

  def find(id: UUID)(
    implicit onFound: File => File
  ): Future[File] = CQL {
    select.where(_.inode_id eqs id)
  }.one().map {
    case None    => throw NotFound(id)
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
          Block.write(curr.id, curr.length, blk)
          val next = curr + blk.size

          next.length < inode.indirect_block_size match {
            case true  => Future.successful(next)
            case false => IndirectBlock.write(next).map(_.next)
          }
      }.mapM { last =>
        IndirectBlock.write(last) iff (last.length != 0)
        this.write(inode.copy(size = last.offset + last.length))
      }

  }

  def purge(id: UUID): Future[ResultSet] = for {
    _ <- IndirectBlock.purge(id)
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
      .value(_.permission, f.permission)
      .value(_.ext_permission, f.ext_permission)
      .value(_.attributes, f.attributes)
  }.future().map(_ => f)
}