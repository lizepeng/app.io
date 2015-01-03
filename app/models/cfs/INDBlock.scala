package models.cfs

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.Assignment
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import common.Logging
import models.cassandra.Cassandra

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class INDBlock(
  inode_id: UUID,
  id: UUID = UUIDs.timeBased(),
  offset: Int = 0,
  length: Int = 0
) {
  def next = INDBlock(inode_id, UUIDs.timeBased(), offset + length, 0)
}

sealed class INDBlocks
  extends CassandraTable[INDBlocks, INDBlock]
  with INodesKey[INDBlocks, INDBlock]
  with INodesDynamic[INDBlocks, INDBlock] {

  override def fromRow(r: Row): INDBlock = {
    INDBlock(inode_id(r), ind_block_id(r), offset(r), length(r))
  }
}

object INDBlock extends INDBlocks with Logging with Cassandra {

  def find(inode_id: UUID, id: UUID): Future[Option[INDBlock]] = {
    select
      .where(_.inode_id eqs inode_id)
      .and(_.ind_block_id eqs id).one()
  }

  def write(ind_blk: INDBlock): Future[ResultSet] = {
    update
      .where(_.inode_id eqs ind_blk.inode_id)
      .and(_.ind_block_id eqs ind_blk.id)
      .modify(_.length setTo ind_blk.length)
      .and(_.offset setTo (ind_blk.offset))
      .future()
  }

  def write(
    inode_id: UUID,
    id: UUID,
    updates: ((INDBlocks) => Assignment)*
  ): Future[Option[ResultSet]] = {
    val stmt = update
      .where(_.inode_id eqs inode_id)
      .and(_.ind_block_id eqs id)
    updates.toList match {
      case Nil          => Future.successful(None)
      case head :: tail => {
        (stmt.modify(head) /: tail)(_ and _)
      }.future().map(Some(_))
    }

  }
}