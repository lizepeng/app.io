package models.cfs

import java.nio.ByteBuffer
import java.util.UUID

import com.datastax.driver.core.utils.{Bytes, UUIDs}
import com.datastax.driver.core.{ResultSet, Row}
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.column.TimeUUIDColumn
import common.Logging
import models.CassandraConnector
import play.api.libs.iteratee._

import scala.concurrent.Future


/**
 * @author zepeng.li@gmail.com
 */
case class Block(
  ind_block_id: UUID,
  block_id: UUID,
  data: ByteBuffer
)


sealed class Blocks extends CassandraTable[Blocks, Block] {

  override val tableName = "blocks"

  object ind_block_id
    extends TimeUUIDColumn(this)
    with PartitionKey[UUID]

  object block_id
    extends TimeUUIDColumn(this)
    with ClusteringOrder[UUID] with Ascending

  object data extends BlobColumn(this)

  override def fromRow(r: Row): Block = {
    Block(ind_block_id(r), block_id(r), data(r))
  }
}

object Block extends Blocks with Logging with CassandraConnector {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  Await.result(create.future(), 500 millis)

  type BLK = Array[Byte]

  def read(ind_blk_id: UUID): Enumerator[BLK] = {
    select(_.data)
      .where(_.ind_block_id eqs ind_blk_id)
      .setFetchSize(CFS.fetchSize)
      .fetchEnumerator().map(Bytes.getArray)
  }

  def read(ind_blk_id: UUID, from: Int, blk_sz: Int): Enumerator[BLK] =
    Enumerator.flatten(
      select(_.block_id)
        .where(_.ind_block_id eqs ind_blk_id)
        .setFetchSize(CFS.fetchSize)
        .fetchEnumerator() &>
        Enumeratee.drop(from / blk_sz) |>>>

        Iteratee.head.map {
          case None         => Enumerator.empty[BLK]
          case Some(blk_id) => {
            select(_.data)
              .where(_.ind_block_id eqs ind_blk_id)
              .and(_.block_id eqs blk_id)
              .fetchEnumerator().map(Bytes.getArray)
              .map(_.drop(from % blk_sz))
          } >>> {
            select(_.data)
              .where(_.ind_block_id eqs ind_blk_id)
              .and(_.block_id gt blk_id)
              .fetchEnumerator().map(Bytes.getArray)
          }
        }
    )

  def write(ind_blk_id: UUID, blk: BLK): Future[ResultSet] = {
    insert.value(_.ind_block_id, ind_blk_id)
      .value(_.block_id, UUIDs.timeBased())
      .value(_.data, ByteBuffer.wrap(blk)).future()
  }

  def remove(ind_blk_id: UUID): Future[ResultSet] = {
    delete.where(_.ind_block_id eqs ind_blk_id).future()
  }
}