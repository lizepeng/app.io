package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import models.cfs.Block.BLK
import models.{TimeBased, User}
import play.api.libs.iteratee._
import play.api.libs.json.Json

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Directory(
  id: UUID = UUIDs.timeBased(),
  parent: UUID,
  owner_id: UUID,
  permission: Long = 7L << 60,
  attributes: Map[String, String] = Map(),
  name: String,
  is_directory: Boolean = true
) extends INode with TimeBased {

  def save(fileName: String = "")(implicit user: User): Iteratee[BLK, File] = {
    val f = File(parent = id, owner_id = user.id, name = fileName)
    val ff = if (!fileName.isEmpty) f else f.copy(name = f.id.toString)
    Iteratee.flatten(add(ff).map(_ => ff.save()))
  }

  def save(): Future[Directory] = Directory.write(this)

  def list(pager: Pager): Future[Page[INode]] = {
    Directory.list(this) |>>>
      PIteratee.slice(pager.start, pager.limit)
  }.map(_.toIterable).map(Page(pager, _))

  private def find(path: Seq[String]): Future[(String, UUID)] = {
    Enumerator(path: _*) |>>>
      Iteratee.foldM((name, id)) {
        case ((_, parentId), childName) =>
          Directory.findChild(parentId, childName)
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

  def file(name: String): Future[File] = {
    Directory.findChild(id, name).flatMap {
      case (n, i) => File.find(i)(_.copy(name = n))
    }
  }

  def mkdir(name: String)(implicit user: User): Future[Directory] = {
    Directory(parent = this.id, owner_id = user.id, name = name).save()
  }

  def mkdir(name: String, uid: UUID): Future[Directory] = {
    Directory(parent = this.id, owner_id = uid, name = name).save()
  }

  def add(inode: INode): Future[Directory] = {
    Directory.addChild(this, inode)
  }

  def del(inode: INode): Future[Directory] = {
    Directory.delChild(this, inode)
  }

  def dir(name: String): Future[Directory] =
    Directory.findChild(id, name).flatMap {
      case (_, i) => Directory.find(i)(_.copy(name = name))
    }

  def dir_!(name: String)(implicit user: User): Future[Directory] =
    Directory.findChild(id, name).flatMap {
      case (_, i) => Directory.find(i)(_.copy(name = name))
    }.recoverWith {
      case e: Directory.ChildNotFound => mkdir(name)
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
      attributes(r),
      ""
    )
  }
}

object Directory extends Directories with Cassandra {

  case class NotFound(id: UUID)
    extends BaseException(CFS.msg_key("dir.not.found"))

  case class ChildNotFound(parent: Any, name: String)
    extends BaseException(CFS.msg_key("dir.child.not.found"))

  case class ChildExists(parent: Any, name: String)
    extends BaseException(CFS.msg_key("dir.child.exists"))

  // Json Reads and Writes
  implicit val directory_writes = Json.writes[Directory]

  def findChild(
    parent: UUID, name: String
  ): Future[(String, UUID)] = CQL {
    select(_.child_id)
      .where(_.inode_id eqs parent)
      .and(_.name eqs name)
  }.one().map {
    case None     => throw ChildNotFound(parent, name)
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
      .ifNotExists()
  }.future().map { rs =>
    if (rs.wasApplied()) dir
    else throw ChildExists(dir.id, inode.name)
  }

  def delChild(
    dir: Directory, inode: INode
  ): Future[Directory] = CQL {
    delete
      .where(_.inode_id eqs dir.id)
      .and(_.name eqs inode.name)
  }.future().map(_ => dir)

  /**
   * TODO We will lost original file content by specifying force flag.
   * But it is still there in DB like a fragment, so we'll need some
   * batch job to remove these orphan files.
   */
  def renameChild(
    dir: Directory,
    inode: INode,
    newName: String,
    force: Boolean = false
  ): Future[INode] = CQL {
    BatchStatement()
      .add {
      delete
        .where(_.inode_id eqs dir.id)
        .and(_.name eqs inode.name)
    }
      .add {
      val cql_insert = insert
        .value(_.inode_id, dir.id)
        .value(_.name, newName)
        .value(_.child_id, inode.id)
      if (force) cql_insert
      else cql_insert.ifNotExists()
    }
  }.future().map(_ => inode)

  /**
   * write name in children list of its parent
   * @param dir
   * @return
   */
  def write(dir: Directory): Future[Directory] =
    if (dir.parent == dir.id)
      cql_write(dir).future().map(_ => dir)
    else for {
      prs <- CQL {
        insert
          .value(_.inode_id, dir.parent)
          .value(_.name, dir.name)
          .value(_.child_id, dir.id)
          .ifNotExists()
      }.future()
      crs <- {
        if (!prs.wasApplied()) {
          Directory.find(child_id(prs.one()))
        } else {
          cql_write(dir).future().map(_ => dir)
        }
      }
    } yield crs

  def cql_write(dir: Directory) = CQL {
    insert
      .value(_.inode_id, dir.id)
      .value(_.parent, dir.parent)
      .value(_.owner_id, dir.owner_id)
      .value(_.permission, dir.permission)
      .value(_.is_directory, true)
      .value(_.attributes, dir.attributes)
      .value(_.name, ".") // with itself as a child
      .value(_.child_id, dir.id)
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