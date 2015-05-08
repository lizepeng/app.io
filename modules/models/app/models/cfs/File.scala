package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import helpers.syntax._
import helpers.{BaseException, Logging}
import models.TimeBased
import models.cassandra.{Cassandra, ExtCQL}
import models.cfs.Block.BLK
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class File(
  id: UUID,
  parent: UUID,
  size: Long,
  indirect_block_size: Int,
  block_size: Int,
  owner_id: UUID,
  permission: Long,
  attributes: Map[String, String],
  name: String
) extends INode with TimeBased {

  def is_directory: Boolean = false

  def read(offset: Long = 0): Enumerator[BLK] =
    if (offset == 0) IndirectBlock.read(id)
    else IndirectBlock.read(this, offset)

  def save(): Iteratee[BLK, File] = File.streamWriter(this)

  override def purge() = {
    super.purge().andThen { case _ => File.purge(id) }
  }
}

/**
 *
 */
sealed class Files
  extends CassandraTable[Files, File]
  with INodeKey[Files, File]
  with INodeColumns[Files, File]
  with FileColumns[Files, File]
  with ExtCQL[Files, File]
  with Logging {

  override def fromRow(r: Row): File = {
    File(
      inode_id(r),
      parent(r),
      size(r),
      indirect_block_size(r),
      block_size(r),
      owner_id(r),
      permission(r),
      attributes(r)
    )
  }
}

object File extends Files with Cassandra {

  case class NotFound(reference: Any)
    extends BaseException(CFS.msg_key("file.not.found"))

  def apply(
    id: UUID = UUIDs.timeBased(),
    parent: UUID,
    size: Long = 0,
    indirect_block_size: Int = 1024 * 32 * 1024 * 8,
    block_size: Int = 1024 * 8,
    owner_id: UUID,
    permission: Long = 6L << 60,
    attributes: Map[String, String] = Map()
  ): File = File(
    id, parent, size, indirect_block_size, block_size,
    owner_id, permission, attributes, id.toString
  )

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
      Iteratee.foldM[BLK, IndirectBlock](IndirectBlock(inode.id)) {
        (curr, blk) =>
          Block.write(curr.id, curr.length, blk)
          val next = curr + blk.size

          next.length < inode.indirect_block_size match {
            case true  => Future.successful(next)
            case false => IndirectBlock.write(next).map(_.next)
          }
      }.mapM { last =>
        IndirectBlock.write(last) iff (last.length != 0)
        File.write(inode.copy(size = last.offset + last.length))
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
      .value(_.attributes, f.attributes)
  }.future().map(_ => f)
}