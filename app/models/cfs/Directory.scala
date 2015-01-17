package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import common.Logging
import controllers.helpers.Pager
import models.TimeBased
import models.cassandra.{Cassandra, DistinctPatch}
import models.cfs.Block.BLK
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Directory(
  id: UUID = UUIDs.timeBased(),
  name: String,
  parent: UUID,
  attributes: Map[String, String] = Map()
) extends INode with TimeBased {
  def is_directory: Boolean = true

  def save(filename: String): Iteratee[BLK, File] = {
    val file = File(name = filename, parent = id)
    Enumeratee.onIterateeDone(() => add(file)) &>> CFS.file.save(file)
  }

  def listFiles(pager: Pager): Future[Iterator[File]] = {
    Directory.list(this) &>
      Enumeratee.filter(!_.is_directory) &>
      Enumeratee.map(_.asInstanceOf[File]) |>>>
      PIteratee.slice(pager.start, pager.limit)
  }

  def add(inode: INode): Directory = Directory.addChild(this, inode)
}

/**
 *
 */
sealed class Directories
  extends CassandraTable[Directories, Directory]
  with INodeKey[Directories, Directory]
  with INodeColumns[Directories, Directory]
  with DirectoryColumns[Directories, Directory]
  with DistinctPatch[Directories, Directory] {

  override def fromRow(r: Row): Directory = {
    Directory(
      inode_id(r),
      name(r),
      parent(r),
      attributes(r)
    )
  }
}

object Directory extends Directories with Logging with Cassandra {

  def find(id: UUID): Future[Option[Directory]] = {
    select.where(_.inode_id eqs id).one()
  }

  def addChild(dir: Directory, inode: INode): Directory = {
    insert
      .value(_.inode_id, dir.id)
      .value(_.child_id, Some(inode.id))
      .future()
    dir
  }

  def write(dir: Directory): Directory = {
    insert
      .value(_.inode_id, dir.id)
      .value(_.name, dir.name)
      .value(_.parent, dir.parent)
      .value(_.is_directory, true)
      .value(_.attributes, dir.attributes)
      .future()
    dir
  }

  def list(dir: Directory): Enumerator[INode] = {
    select(_.child_id)
      .where(_.inode_id eqs dir.id)
      .setFetchSize(CFS.listFetchSize)
      .fetchEnumerator() &>
      Enumeratee.filter(_.isDefined) &>
      Enumeratee.map(_.get) &>
      Enumeratee.mapM(INode.find) &>
      Enumeratee.filter(_.isDefined) &>
      Enumeratee.map(_.get)
  }
}