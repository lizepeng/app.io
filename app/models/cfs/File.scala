package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import helpers.syntax._
import models.TimeBased
import models.cassandra.{Cassandra, DistinctPatch}
import models.cfs.Block.BLK
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class File(
  id: UUID = UUIDs.timeBased(),
  parent: UUID,
  size: Long = 0,
  indirect_block_size: Int = 1024 * 32 * 1024 * 8,
  block_size: Int = 1024 * 8,
  attributes: Map[String, String] = Map(),
  name: String = ""
) extends INode with TimeBased {
  def is_directory: Boolean = false
}

/**
 *
 */
sealed class Files
  extends CassandraTable[Files, File]
  with INodeKey[Files, File]
  with INodeColumns[Files, File]
  with FileColumns[Files, File]
  with DistinctPatch[Files, File] {

  override def fromRow(r: Row): File = {
    File(
      inode_id(r),
      parent(r),
      size(r),
      indirect_block_size(r),
      block_size(r),
      attributes(r)
    )
  }
}

object File extends Files with Logging with Cassandra {

  def findBy(id: UUID)(
    implicit onFound: File => File
  ): Future[Option[File]] = {
    select
      .where(_.inode_id eqs id)
      .one()
      .map(_.map(onFound))
  }

  def streamWriter(inode: File): Iteratee[BLK, File] = {

    Enumeratee.grouped[BLK] {
      Traversable.take[BLK](inode.block_size) &>>
        Iteratee.consume[BLK]()
    } &>>
      Iteratee.fold[BLK, IndirectBlock](IndirectBlock(inode.id)) {
        (curr, blk) =>
          Block.write(curr.id, curr.length, blk)
          val next = curr + blk.size

          if (next.length < inode.indirect_block_size) next
          else IndirectBlock.write(next).next
      }.map {last =>
        IndirectBlock.write(last) iff (last.length != 0)
        File.write(inode.copy(size = last.offset + last.length))
      }

  }

  def purge(id: UUID): Future[ResultSet] = {
    IndirectBlock.purge(id)
    delete.where(_.inode_id eqs id).future()
  }

  private def write(inode: File): File = {
    insert.value(_.inode_id, inode.id)
      //      .value(_.name, inode.name)
      .value(_.parent, inode.parent)
      .value(_.is_directory, false)
      .value(_.size, inode.size)
      .value(_.indirect_block_size, inode.indirect_block_size)
      .value(_.block_size, inode.block_size)
      .value(_.attributes, inode.attributes)
      .future()
    inode
  }

}