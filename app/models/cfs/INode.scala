package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import common.Logging
import common.syntax._
import models.cfs.Block.BLK
import models.{CassandraConnector, TimeBased}
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class INode(
  id: UUID = UUIDs.timeBased(),
  name: String,
  parent: UUID,
  is_directory: Boolean = false,
  children: Map[String, UUID] = Map(),
  size: Int = 0,
  block_count: Int = 0,
  ind_block_length: Int = 1024 * 32,
  block_size: Int = 1024 * 8,
  attributes: Map[String, String] = Map()
) extends TimeBased {

  lazy val ind_block_size = ind_block_length * block_size

}

/**
 *
 */
sealed class INodes
  extends CassandraTable[INodes, INode]
  with INodesKey[INodes, INode]
  with INodesStatic[INodes, INode]
  with INodesDynamic[INodes, INode] {

  override def fromRow(r: Row): INode = {
    INode(
      inode_id(r),
      name(r),
      parent(r),
      is_directory(r),
      children(r),
      size(r),
      block_count(r),
      ind_block_length(r),
      block_size(r),
      attributes(r)
    )
  }
}


object INode extends INodes with Logging with CassandraConnector {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  Await.result(create.future(), 500 millis)


  def find(id: UUID): Future[Option[INode]] = {
    select.where(_.inode_id eqs id).one()
  }

  def read(inode: INode): Enumerator[BLK] = {
    select(_.ind_block_id).where(_.inode_id eqs inode.id)
      .setFetchSize(CFS.fetchSize)
      .fetchEnumerator() &>
      Enumeratee.mapFlatten[UUID](Block.read)
  }

  def read(inode: INode, from: Int): Enumerator[BLK] =
    Enumerator.flatten(
      select(_.ind_block_id).where(_.inode_id eqs inode.id)
        .setFetchSize(CFS.fetchSize)
        .fetchEnumerator() &>
        Enumeratee.drop[UUID](from / inode.ind_block_size) |>>>

        Iteratee.head.map {
          case None             => Enumerator.empty
          case Some(ind_blk_id) => {
            Block.read(ind_blk_id, from % inode.ind_block_size, inode.block_size)
          } >>> {
            select(_.ind_block_id)
              .where(_.inode_id eqs inode.id)
              .and(_.ind_block_id gt ind_blk_id)
              .fetchEnumerator() &>
              Enumeratee.mapFlatten[UUID](Block.read)
          }
        }
    )

  def write(inode: INode): Future[ResultSet] = {
    insert.value(_.inode_id, inode.id)
      .value(_.name, inode.name)
      .value(_.parent, inode.parent)
      .value(_.is_directory, inode.is_directory)
      .value(_.children, inode.children)
      .value(_.size, inode.size)
      .value(_.block_count, inode.block_count)
      .value(_.ind_block_length, inode.ind_block_length)
      .value(_.block_size, inode.block_size)
      .value(_.attributes, inode.attributes)
      .future()
  }

  def streamWriter(inode: INode): Iteratee[BLK, INode] = {
    Enumeratee.grouped[BLK] {
      Traversable.take[BLK](inode.block_size) &>>
        Iteratee.consume[BLK]()
    } &>> iteratee(inode)
  }

  def iteratee(inode: INode): Iteratee[BLK, INode] = {
    case class State(
      blk_cnt: Int = 0,
      offset: Int = 0,
      size: Int = 0,
      ind_blk: Option[INDBlock] = None
    )

    Iteratee.fold[BLK, State](State()) {(curr, blk) =>
      val full = curr.blk_cnt % inode.ind_block_length == 0
      curr.ind_blk.foreach(INDBlock.write) iff full

      val next = curr.copy(
        blk_cnt = curr.blk_cnt + 1,
        size = curr.size + blk.size,
        ind_blk =
          if (full) {
            curr.ind_blk match {
              case None    => Some(INDBlock(inode.id))
              case Some(b) => Some(b.next)
            }
          }
          else curr.ind_blk.map {b =>
            b.copy(length = b.length + blk.size)
          }
      )

      next.ind_blk.map {ind_blk =>
        Block.write(ind_blk.id, blk)
      }
      next
    }.map {finished =>
      finished.ind_blk.foreach(INDBlock.write)
      inode.copy(size = finished.size, block_count = finished.blk_cnt)
    }.map {n => INode.write(n); n}
  }

  def remove(id: UUID): Future[ResultSet] = {
    select(_.ind_block_id).where(_.inode_id eqs id)
      .fetchEnumerator() |>>> Iteratee.foreach(Block.remove(_))
    delete.where(_.inode_id eqs id).future()
  }

  def all(): Future[Seq[INode]] = {
    val fetchSize: Int = 100
    //TODO changed to Distinct
    select(_.inode_id)
      .setFetchSize(fetchSize)
      .fetchEnumerator() &>
      Enumeratee.grouped[UUID] {
        Enumeratee.take[UUID](fetchSize) &>> Iteratee.getChunks[UUID]
      } &> Enumeratee.mapFlatten {
      ids =>
        select.where(_.inode_id in ids)
          .setFetchSize(fetchSize)
          .fetchEnumerator()
    } |>>> Iteratee.getChunks[INode].map(_.distinct)
  }
}