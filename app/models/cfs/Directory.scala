package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
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
  parent: UUID,
  attributes: Map[String, String] = Map(),
  name: String = ""
) extends INode with TimeBased {
  def is_directory: Boolean = true

  def save(filename: String): Iteratee[BLK, File] = {
    val file = File(name = filename, parent = id)
    Enumeratee.onIterateeDone(() => add(file)) &>> CFS.file.save(file)
  }

  def listFiles(pager: Pager): Future[Iterator[File]] = {
    Directory.list(this) &>
      Enumeratee.map {
        case (name, inode: File) => Some(inode.copy(name = name))
        case _                   => None
      } &> Enumeratee.flattenOption |>>>
      PIteratee.slice(pager.start, pager.limit)
  }

  def add(inode: INode): Directory = {
    Directory.addChild(this, inode)
  }

  def dir(name: String): Future[Option[Directory]] = {
    CFS.dir.findBy(this, name)
  }

  def dir(path: Path): Future[Option[Directory]] = {
    CFS.dir.findBy(this, path)
  }

  def file(name: String): Future[Option[File]] = {
    CFS.file.findBy(this, name)
  }
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
      parent(r),
      attributes(r)
    )
  }
}

object Directory extends Directories with Logging with Cassandra {

  def findChildBy(
    parent: Directory, path: Path
  ): Future[Option[(String, UUID)]] = {
    Enumerator(path.dirs: _*) |>>>
      Iteratee.foldM[String, Option[(String, UUID)]](
        Some(parent.name, parent.id)
      ) {
        (directory, sub) => directory match {
          case None           => Future.successful(None)
          case Some((_, dir)) => findChildBy(dir, sub)
        }
      }
  }

  def findChildBy(
    parent: UUID, name: String
  ): Future[Option[(String, UUID)]] = {
    select(_.child_id)
      .where(_.inode_id eqs parent)
      .and(_.name eqs name)
      .one()
      .map(_.map((name, _)))
  }

  def findBy(id: UUID)(
    implicit onFound: Directory => Directory
  ): Future[Option[Directory]] = {
    select
      .where(_.inode_id eqs id)
      .one()
      .map(_.map(onFound))
  }

  def addChild(
    dir: Directory, inode: INode
  ): Directory = {
    insert
      .value(_.inode_id, dir.id)
      .value(_.name, inode.name)
      .value(_.child_id, inode.id)
      .future()
    dir
  }

  /**
   * write name in children list of its parent
   * @param dir
   * @return
   */
  def write(dir: Directory): Directory = {
    val batch = BatchStatement().add {
      insert
        .value(_.inode_id, dir.id)
        .value(_.name, ".")
        .value(_.child_id, dir.id)
        .value(_.parent, dir.parent)
        .value(_.is_directory, true)
        .value(_.attributes, dir.attributes)
    }
    if (dir.parent != dir.id) batch.add {
      insert
        .value(_.inode_id, dir.parent)
        .value(_.name, dir.name)
        .value(_.child_id, dir.id)
    }
    batch.future()
    dir
  }

  /**
   *
   * @param dir
   * @return
   */
  def list(dir: Directory): Enumerator[(String, INode)] = {
    select(_.name, _.child_id)
      .where(_.inode_id eqs dir.id)
      .setFetchSize(CFS.listFetchSize)
      .fetchEnumerator() &>
      Enumeratee.mapM {
        case (name, id) => INode.find(id).map {
          case None        => None
          case Some(inode) => Some((name, inode))
        }
      } &> Enumeratee.flattenOption
  }
}