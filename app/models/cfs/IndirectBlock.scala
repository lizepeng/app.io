package models.cfs

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import models.cassandra.Cassandra
import models.cfs.Block._
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class IndirectBlock(
  inode_id: UUID,
  offset: Long = 0,
  length: Long = 0,
  id: UUID = UUIDs.timeBased()
) {
  def next = IndirectBlock(inode_id, offset + length, 0, UUIDs.timeBased())

  def +(length: Int) = IndirectBlock(inode_id, offset, this.length + length, id)
}

sealed class IndirectBlocks
  extends CassandraTable[IndirectBlocks, IndirectBlock]
  with INodeKey[IndirectBlocks, IndirectBlock] {

  override val tableName = "indirect_blocks"

  object offset
    extends LongColumn(this)
    with ClusteringOrder[Long] with Ascending

  object length extends LongColumn(this)

  object indirect_block_id extends TimeUUIDColumn(this)

  override def fromRow(r: Row): IndirectBlock = {
    IndirectBlock(inode_id(r), offset(r), length(r), indirect_block_id(r))
  }
}

object IndirectBlock extends IndirectBlocks with Cassandra {

  def read(file: File): Enumerator[BLK] = {
    select(_.indirect_block_id)
      .where(_.inode_id eqs file.id)
      .setFetchSize(CFS.streamFetchSize)
      .fetchEnumerator() &>
      Enumeratee.mapFlatten[UUID](Block.read)
  }

  def read(file: File, offset: Long): Enumerator[BLK] = Enumerator.flatten {
    select(_.indirect_block_id)
      .where(_.inode_id eqs file.id)
      .and(_.offset eqs offset - offset % file.indirect_block_size)
      .one().map {
      case None     => Enumerator.empty[BLK]
      case Some(id) => {
        Block.read(id, offset % file.indirect_block_size, file.block_size)
      }
    }.map {
      _ >>> (select(_.indirect_block_id)
        .where(_.inode_id eqs file.id)
        .and(_.offset gt offset - offset % file.indirect_block_size)
        .setFetchSize(CFS.streamFetchSize)
        .fetchEnumerator() &>
        Enumeratee.mapFlatten[UUID](Block.read))
    }
  }

  def write(ind_blk: IndirectBlock): IndirectBlock = {
    update
      .where(_.inode_id eqs ind_blk.inode_id)
      .and(_.offset eqs ind_blk.offset)
      .modify(_.length setTo ind_blk.length)
      .and(_.indirect_block_id setTo ind_blk.id)
      .future()
    ind_blk
  }

  def purge(id: UUID): Future[ResultSet] = {
    select(_.indirect_block_id)
      .where(_.inode_id eqs id)
      .setFetchSize(CFS.listFetchSize)
      .fetchEnumerator() |>>>
      Iteratee.foreach(Block.purge(_))

    delete.where(_.inode_id eqs id).future()
  }
}