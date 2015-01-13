package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import common.Logging
import common.syntax._
import models.TimeBased
import models.cassandra.{Cassandra, DistinctPatch}
import models.cfs.Block.BLK
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
  size: Long = 0,
  indirect_block_size: Int = 1024 * 32 * 1024 * 8,
  block_size: Int = 1024 * 8,
  attributes: Map[String, String] = Map()
) extends TimeBased

/**
 *
 */
sealed class INodes
  extends CassandraTable[INodes, INode]
  with INodesKey[INodes, INode]
  with INodesStatic[INodes, INode]
  with INodesDynamic[INodes, INode]
  with DistinctPatch[INodes, INode] {

  override def fromRow(r: Row): INode = {
    INode(
      inode_id(r),
      name(r),
      parent(r),
      is_directory(r),
      size(r),
      indirect_block_size(r),
      block_size(r),
      attributes(r)
    )
  }
}

object INode extends INodes with Logging with Cassandra {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  Await.result(create.future(), 500 millis)

  def find(id: UUID): Future[Option[INode]] = {
    select.where(_.inode_id eqs id).one()
  }

  def read(inode: INode): Enumerator[BLK] = {
    select(_.indirect_block_id)
      .where(_.inode_id eqs inode.id)
      .setFetchSize(CFS.streamFetchSize)
      .fetchEnumerator() &>
      Enumeratee.mapFlatten[UUID](Block.read)
  }

  def read(inode: INode, offset: Long): Enumerator[BLK] = Enumerator.flatten {
    select(_.indirect_block_id)
      .where(_.inode_id eqs inode.id)
      .and(_.offset eqs offset - offset % inode.indirect_block_size)
      .one().map {
      case None     => Enumerator.empty[BLK]
      case Some(id) => {
        Block.read(id, offset % inode.indirect_block_size, inode.block_size)
      }
    }.map {
      _ >>> (select(_.indirect_block_id)
        .where(_.inode_id eqs inode.id)
        .and(_.offset gt offset - offset % inode.indirect_block_size)
        .setFetchSize(CFS.streamFetchSize)
        .fetchEnumerator() &>
        Enumeratee.mapFlatten[UUID](Block.read))
    }
  }

  def write(inode: INode): INode = {
    insert.value(_.inode_id, inode.id)
      .value(_.name, inode.name)
      .value(_.parent, inode.parent)
      .value(_.is_directory, inode.is_directory)
      .value(_.size, inode.size)
      .value(_.indirect_block_size, inode.indirect_block_size)
      .value(_.block_size, inode.block_size)
      .value(_.attributes, inode.attributes)
      .future()
    inode
  }

  def streamWriter(inode: INode): Iteratee[BLK, INode] = {

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
        INode.write(inode.copy(size = last.offset + last.length))
      }

  }

  def purge(id: UUID) {
    select(_.indirect_block_id)
      .where(_.inode_id eqs id)
      .setFetchSize(CFS.listFetchSize)
      .fetchEnumerator() |>>>
      Iteratee.foreach(Block.purge(_))

    delete.where(_.inode_id eqs id).future()
  }

  def page(start: Int, limit: Int): Future[Iterator[INode]] = {
    distinct(_.inode_id)
      .setFetchSize(CFS.listFetchSize)
      .fetchEnumerator() &>
      Enumeratee.mapM {id =>
        select.where(_.inode_id eqs id).one()
      } &>
      Enumeratee.filter(_.isDefined) &>
      Enumeratee.map(_.get) |>>>
      PIteratee.slice(start, limit)
  }
}