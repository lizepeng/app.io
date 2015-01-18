package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import common.Logging
import models.TimeBased
import models.cassandra.{Cassandra, DistinctPatch}
import models.cfs.Block.BLK
import models.helpers.Pager
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
      Enumeratee.filter(!_.is_directory) &>
      Enumeratee.map(_.asInstanceOf[File]) |>>>
      PIteratee.slice(pager.start, pager.limit)
  }

  def add(inode: INode): Directory = {
    Directory.addChild(this, inode)
  }

  def find(name: String): Future[Option[Directory]] = {
    Directory.findBy(this, name)
  }

  def findFile(name: String): Future[Option[File]] = {
    Directory.findFileBy(this, name)
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

  def findFileBy(
    parent: Directory, path: Path
  ): Future[Option[File]] = {
    path.filename match {
      case None           => Future.successful(None)
      case Some(filename) => findChildBy(parent, path).flatMap {
        case None          => Future.successful(None)
        case Some((_, id)) => findFileBy(id, filename)
      }
    }
  }

  def findFileBy(
    parent_id: UUID, name: String
  ): Future[Option[File]] = {
    findChildBy(parent_id, name).flatMap {
      case Some((_, id)) => File.findBy(id)(_.copy(name = name))
      case None          => Future.successful(None)
    }
  }

  def findFileBy(
    parent: Directory, name: String
  ): Future[Option[File]] = {
    findFileBy(parent.id, name)
  }

  private def findChildBy(
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

  def findBy(
    parent: Directory, path: Path
  ): Future[Option[Directory]] = {
    findChildBy(parent, path).flatMap {
      case None             => Future.successful(None)
      case Some((name, id)) => findBy(id)(_.copy(name = name))
    }
  }

  def findBy(
    parent: Directory, name: String
  ): Future[Option[Directory]] = {
    findChildBy(parent.id, name).flatMap {
      case Some((_, id)) => Directory.findBy(id)(_.copy(name = name))
      case None          => Future.successful(None)
    }
  }

  def findBy(id: UUID)(
    implicit onFound: Directory => Directory
  ): Future[Option[Directory]] = {
    select
      .where(_.inode_id eqs id)
      .one()
      .map(_.map(onFound))
  }

  private def findChildBy(
    parent: UUID, name: String
  ): Future[Option[(String, UUID)]] = {
    select(_.child_id)
      .where(_.inode_id eqs parent)
      .and(_.name eqs name)
      .one()
      .map(_.map((name, _)))
  }

  def addChild(
    dir: Directory, inode: INode
  ): Directory = {
    insert
      .value(_.inode_id, dir.id)
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
    insert
      .value(_.inode_id, dir.id)
      //      .value(_.name, dir.name)
      .value(_.parent, dir.parent)
      .value(_.is_directory, true)
      .value(_.attributes, dir.attributes)
      .future()
    dir
  }

  /**
   * TODO Directory may have no findChildBy
   * @param dir
   * @return
   */
  def list(dir: Directory): Enumerator[INode] = {
    select(_.child_id)
      .where(_.inode_id eqs dir.id)
      .setFetchSize(CFS.listFetchSize)
      .fetchEnumerator() &>
      Enumeratee.mapM(INode.find) &>
      Enumeratee.filter(_.isDefined) &>
      Enumeratee.map(_.get)
  }
}