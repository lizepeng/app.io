package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import models.cfs.Block.BLK
import models.{CanonicalNamedModel, User}
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Directory(
  name: String,
  path: Path,
  owner_id: UUID,
  parent: UUID,
  id: UUID = UUIDs.timeBased(),
  permission: Long = 7L << 60,
  ext_permission: Map[UUID, Int] = Map(),
  attributes: Map[String, String] = Map(),
  is_directory: Boolean = true
) extends INode {

  def save(fileName: String = "")(
    implicit user: User, Directory: DirectoryRepo, File: FileRepo
  ): Iteratee[BLK, File] = {
    val f = new File(fileName, path + fileName, user.id, id)
    val ff =
      if (!fileName.isEmpty) f
      else f.copy(name = f.id.toString, path = path + f.id.toString)
    Iteratee.flatten(add(ff).map(_ => ff.save()))
  }

  def save()
      (implicit Directory: DirectoryRepo): Future[Directory] = Directory.write(this)

  def list(pager: Pager)
      (implicit Directory: DirectoryRepo): Future[Page[INode]] = {
    Directory.list(this) |>>>
      PIteratee.slice(pager.start, pager.limit)
  }.map(_.toIterable).map(Page(pager, _))

  private def find(path: Seq[String])
      (implicit Directory: DirectoryRepo): Future[(String, UUID)] = {
    Enumerator(path: _*) |>>>
      Iteratee.foldM((name, id)) {
        case ((_, parentId), childName) =>
          Directory.findChild(parentId, childName)
      }
  }

  def dir(path: Path)(implicit Directory: DirectoryRepo): Future[Directory] =
    if (path.filename.nonEmpty)
      Future.failed(models.cfs.Directory.NotDirectory(path))
    else find(path.parts).flatMap {
      case (n, i) => Directory.find(i)(
        _.copy(name = n, path = path)
      )
    }

  def file(path: Path)
      (implicit Directory: DirectoryRepo, File: FileRepo): Future[File] = {
    find(path.parts ++ path.filename).flatMap {
      case (n, i) => File.find(i)(
        _.copy(name = n, path = path)
      )
    }
  }

  def file(name: String)
      (implicit Directory: DirectoryRepo, File: FileRepo): Future[File] = {
    Directory.findChild(id, name).flatMap {
      case (n, i) => File.find(i)(
        _.copy(name = n, path = path + name)
      )
    }
  }

  def mkdir(name: String)
      (implicit user: User, Directory: DirectoryRepo): Future[Directory] = {
    new Directory(name, path / name, user.id, id).save()
  }

  def mkdir(name: String, uid: UUID)
      (implicit Directory: DirectoryRepo): Future[Directory] = {
    new Directory(name, path / name, uid, id).save()
  }

  def add(inode: INode)
      (implicit Directory: DirectoryRepo): Future[Directory] = {
    Directory.addChild(this, inode)
  }

  def del(inode: INode)
      (implicit Directory: DirectoryRepo): Future[Directory] = {
    Directory.delChild(this, inode)
  }

  def dir(name: String)(implicit Directory: DirectoryRepo): Future[Directory] =
    Directory.findChild(id, name).flatMap {
      case (_, i) => Directory.find(i)(
        _.copy(name = name, path = path / name)
      )
    }

  def dir_!(name: String)
      (implicit user: User, Directory: DirectoryRepo): Future[Directory] =
    Directory.findChild(id, name).flatMap {
      case (_, i) => Directory.find(i)(
        _.copy(name = name, path = path / name)
      )
    }.recoverWith {
      case e: models.cfs.Directory.ChildNotFound => mkdir(name)
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
  with CanonicalNamedModel[Directory]
  with Logging {

  override def fromRow(r: Row): Directory = {
    Directory(
      "",
      Path(),
      owner_id(r),
      parent(r),
      inode_id(r),
      permission(r),
      ext_permission(r),
      attributes(r)
    )
  }
}

object Directory
  extends Directories
  with ExceptionDefining {

  case class NotFound(id: UUID)
    extends BaseException(error_code("dir.not.found"))

  case class ChildNotFound(parent: Any, name: String)
    extends BaseException(error_code("dir.child.not.found"))

  case class ChildExists(parent: Any, name: String)
    extends BaseException(error_code("dir.child.exists"))

  case class NotDirectory(path: Path)
    extends BaseException(error_code("dir.not.dir"))

}

class DirectoryRepo(
  implicit
  val INode: INodeRepo,
  val basicPlayApi: BasicPlayApi
)
  extends Directories
  with ExtCQL[Directories, Directory]
  with BasicPlayComponents
  with Cassandra {

  import Directory._

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
    Batch.logged
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
          this.find(child_id(prs.one()))
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
      .value(_.ext_permission, dir.ext_permission)
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
  }.fetchEnumerator() &>
    Enumeratee.mapM {
      case (name, id) => INode.find(id).map[Option[INode]] {
        case Some(nd: File)      =>
          Some(nd.copy(name = name, path = dir.path + name))
        case Some(nd: Directory) =>
          Some(nd.copy(name = name, path = dir.path / name))
        case _                   =>
          None
      }
    } &> Enumeratee.flattenOption

}