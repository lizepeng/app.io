package models.cfs

import java.nio.ByteBuffer
import java.util.UUID

import akka.util._
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Block(
  indirect_block_id: UUID,
  offset: Long,
  data: ByteBuffer
)

trait BlockCanonicalNamed extends CanonicalNamed {

  override val basicName = "blocks"
}

sealed class BlockTable
  extends NamedCassandraTable[BlockTable, Block]
  with BlockCanonicalNamed {

  object indirect_block_id
    extends TimeUUIDColumn(this)
    with PartitionKey[UUID]

  object offset
    extends LongColumn(this)
    with ClusteringOrder[Long] with Ascending

  object data
    extends BlobColumn(this)

  override def fromRow(r: Row): Block = {
    Block(indirect_block_id(r), offset(r), data(r))
  }
}

object Block extends BlockCanonicalNamed {

  type BLK = ByteString
}

class Blocks(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends BlockTable
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess {

  onStart(create.ifNotExists.future())

  import Block._

  def read(ind_blk_id: UUID): Enumerator[BLK] = {
    select(_.data)
      .where(_.indirect_block_id eqs ind_blk_id)
      .fetchEnumerator().map(ByteString.fromByteBuffer)
  }

  def read(ind_blk_id: UUID, offset: Long, blk_sz: Int): Enumerator[BLK] = {
    Enumerator.flatten(
      select(_.data)
        .where(_.indirect_block_id eqs ind_blk_id)
        .and(_.offset eqs offset - offset % blk_sz)
        .one().map(_.map(ByteString.fromByteBuffer))
        .map {
          case None      => Enumerator.empty[BLK]
          case Some(blk) => Enumerator(blk.drop((offset % blk_sz).toInt))
        }.map {
        _ >>> select(_.data)
          .where(_.indirect_block_id eqs ind_blk_id)
          .and(_.offset gt offset - offset % blk_sz)
          .fetchEnumerator().map(ByteString.fromByteBuffer)
      }
    )
  }

  def write(ind_blk_id: UUID, blk_id: Long, blk: BLK): Future[ResultSet] = {
    insert.value(_.indirect_block_id, ind_blk_id)
      .value(_.offset, blk_id)
      .value(_.data, blk.toByteBuffer).future()
  }

  def purge(ind_blk_id: UUID): Future[ResultSet] = {
    delete.where(_.indirect_block_id eqs ind_blk_id).future()
  }
}