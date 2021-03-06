package models.cfs

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.cfs.Block._
import play.api.libs.iteratee._

import scala.concurrent.Future
import scala.concurrent.duration._

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

trait IndirectBlockCanonicalNamed extends CanonicalNamed {

  override val basicName = "indirect_blocks"
}

sealed class IndirectBlockTable
  extends NamedCassandraTable[IndirectBlockTable, IndirectBlock]
    with IndirectBlockCanonicalNamed
    with INodeKey[IndirectBlockTable, IndirectBlock] {

  object offset
    extends LongColumn(this)
      with ClusteringOrder[Long] with Ascending

  object length
    extends LongColumn(this)

  object indirect_block_id
    extends TimeUUIDColumn(this)

  override def fromRow(r: Row): IndirectBlock = {
    IndirectBlock(inode_id(r), offset(r), length(r), indirect_block_id(r))
  }
}

object IndirectBlock extends IndirectBlockCanonicalNamed

class IndirectBlocks(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef,
  val _blocks: Blocks
) extends IndirectBlockTable
  with ExtCQL[IndirectBlockTable, IndirectBlock]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def read(id: UUID): Enumerator[BLK] = {
    select(_.indirect_block_id)
      .where(_.inode_id eqs id)
      .fetchEnumerator() &>
      Enumeratee.mapFlatten[UUID](_blocks.read)
  }

  def read(file: File, offset: Long): Enumerator[BLK] = Enumerator.flatten {
    select(_.indirect_block_id)
      .where(_.inode_id eqs file.id)
      .and(_.offset eqs offset - offset % file.indirect_block_size)
      .one().map {
      case None     => Enumerator.empty[BLK]
      case Some(id) => _blocks.read(id, offset % file.indirect_block_size, file.block_size)
    }.map {
      _ >>> (
        select(_.indirect_block_id)
          .where(_.inode_id eqs file.id)
          .and(_.offset gt offset - offset % file.indirect_block_size)
          .fetchEnumerator() &>
          Enumeratee.mapFlatten[UUID](_blocks.read))
    }
  }

  def write(
    ind_blk: IndirectBlock, ttl: Duration = Duration.Inf
  ): Future[IndirectBlock] = {
    val cql =
      update
        .where(_.inode_id eqs ind_blk.inode_id)
        .and(_.offset eqs ind_blk.offset)
        .modify(_.length setTo ind_blk.length)
        .and(_.indirect_block_id setTo ind_blk.id)
    (ttl match {
      case t: FiniteDuration => cql.ttl(t)
      case _                 => cql
    }).future().map(_ => ind_blk)
  }

  def purge(id: UUID) = {
    select(_.indirect_block_id)
      .where(_.inode_id eqs id)
      .fetchEnumerator() &>
      Enumeratee.onIterateeDone { () =>
        CQL {delete.where(_.inode_id eqs id)}.future()
      } |>>> Iteratee.foreach[UUID](_blocks.purge(_))
  }

  def isEmpty(id: UUID): Future[Boolean] = CQL {
    select(_.indirect_block_id)
      .where(_.inode_id eqs id)
  }.one().map(_.isEmpty).recover {
    case e: Exception => true
  }
}