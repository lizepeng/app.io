package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import models.cfs.Block.BLK
import models.{TimeBased, User}
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Directory(
  id: UUID = UUIDs.timeBased(),
  parent: UUID,
  owner_id: UUID,
  permission: Long = 0L,
  attributes: Map[String, String] = Map(),
  name: String = ""
) extends INode with TimeBased {
  def is_directory: Boolean = true

  def save(filename: String)(implicit user: User): Iteratee[BLK, File] = {
    val f = File(parent = id, name = filename, owner_id = user.id)
    Enumeratee.onIterateeDone(() => add(f)) &>> f.save()
  }

  def save(): Future[Directory] = Directory.write(this)

  def list(pager: Pager): Future[Iterator[INode]] = {
    Directory.list(this) |>>>
      PIteratee.slice(pager.start, pager.limit)
  }

  private def find(path: Seq[String]): Future[(String, UUID)] = {
    Enumerator(path: _*) |>>>
      Iteratee.foldM((name, id)) {
        case ((_, pi), cn) => Directory.findChild(pi, cn)
      }
  }

  def dir(path: Path): Future[Directory] = {
    find(path.parts).flatMap {
      case (n, i) => Directory.find(i)(_.copy(name = n))
    }
  }

  def file(path: Path): Future[File] = {
    find(path.parts ++ path.filename).flatMap {
      case (n, i) => File.find(i)(_.copy(name = n))
    }
  }

  def add(inode: INode): Future[Directory] = {
    Directory.addChild(this, inode)
  }

  def dir(name: String): Future[Directory] = {
    Directory.findChild(id, name).flatMap {
      case (_, i) => Directory.find(i)(_.copy(name = name))
    }
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
  with ExtCQL[Directories, Directory]
  with Logging {

  override def fromRow(r: Row): Directory = {
    Directory(
      inode_id(r),
      parent(r),
      owner_id(r),
      permission(r),
      attributes(r)
    )
  }
}

object Directory extends Directories with Cassandra {

  case class NotFound(id: UUID)
    extends BaseException("cfs.dir.not.found")

  case class ChildNotFound(name: String)
    extends BaseException("cfs.child.not.found")

  def findChild(
    parent: UUID, name: String
  ): Future[(String, UUID)] = CQL {
    select(_.child_id)
      .where(_.inode_id eqs parent)
      .and(_.name eqs name)
  }.one().map {
    case None     => throw ChildNotFound(name)
    case Some(id) => (name, id)
  }

  def find(id: UUID)(
    implicit onFound: Directory => Directory
  ): Future[Directory] = CQL {
    select
      .where(_.inode_id eqs id)
  }.one().map {
    case None    => throw NotFound(id)
    case Some(d) => onFound(d)
  }

  def addChild(
    dir: Directory, inode: INode
  ): Future[Directory] = CQL {
    insert
      .value(_.inode_id, dir.id)
      .value(_.name, inode.name)
      .value(_.child_id, inode.id)
  }.future().map(_ => dir)

  /**
   * write name in children list of its parent
   * @param dir
   * @return
   */
  def write(dir: Directory): Future[Directory] = {
    val batch = BatchStatement().add {
      CQL {
        insert
          .value(_.inode_id, dir.id)
          .value(_.name, ".")
          .value(_.child_id, dir.id)
          .value(_.parent, dir.parent)
          .value(_.owner_id, dir.owner_id)
          .value(_.permission, dir.permission)
          .value(_.is_directory, true)
          .value(_.attributes, dir.attributes)
      }
    }
    if (dir.parent != dir.id) batch.add {
      CQL {
        insert
          .value(_.inode_id, dir.parent)
          .value(_.name, dir.name)
          .value(_.child_id, dir.id)
      }
    }
    batch.future().map(_ => dir)
  }

  /**
   *
   * @param dir
   * @return
   */
  def list(dir: Directory): Enumerator[INode] = CQL {
    select(_.name, _.child_id)
      .where(_.inode_id eqs dir.id)
      .setFetchSize(CFS.listFetchSize)
  }.fetchEnumerator() &>
    Enumeratee.mapM {
      case (name, id) => INode.find(id).map[Option[INode]] {
        case Some(nd: File)      => Some(nd.copy(name = name))
        case Some(nd: Directory) => Some(nd.copy(name = name))
        case _                   => None
      }
    } &> Enumeratee.flattenOption

}