package models.cfs

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import models.cassandra.Cassandra

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
  with INodesKey[IndirectBlocks, IndirectBlock]
  with INodesDynamic[IndirectBlocks, IndirectBlock] {

  override def fromRow(r: Row): IndirectBlock = {
    IndirectBlock(inode_id(r), offset(r), length(r), indirect_block_id(r))
  }
}

object IndirectBlock extends IndirectBlocks with Cassandra {

  def write(ind_blk: IndirectBlock): IndirectBlock = {
    update
      .where(_.inode_id eqs ind_blk.inode_id)
      .and(_.offset eqs ind_blk.offset)
      .modify(_.length setTo ind_blk.length)
      .and(_.indirect_block_id setTo ind_blk.id)
      .future()
    ind_blk
  }
}