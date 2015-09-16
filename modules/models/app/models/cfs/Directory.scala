package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers.ExtEnumeratee._
import helpers._
import models.User
import models.cassandra._
import models.cfs.Block.BLK
import play.api.libs.iteratee.{Enumeratee => _, _}
import play.api.libs.json._

import scala.concurrent.Future
import scala.util.Success

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
    implicit user: User, cfs: CassandraFileSystem
  ): Iteratee[BLK, File] = {
    val f = new File(fileName, path + fileName, user.id, id)
    val ff =
      if (!fileName.isEmpty) f
      else f.copy(name = f.id.toString, path = path + f.id.toString)
    Iteratee.flatten(add(ff).map(_ => ff.save()))
  }

  def save()(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.write(this)

  def list(pager: Pager)(
    implicit cfs: CassandraFileSystem
  ): Future[Page[INode]] = {
    cfs._directories.stream(this) |>>>
      PIteratee.slice(pager.start, pager.limit)
  }.map(_.toIterable).map(Page(pager, _))

  private def find(path: Seq[String])(
    implicit cfs: CassandraFileSystem
  ): Future[(String, UUID)] = {
    Enumerator(path: _*) |>>>
      Iteratee.foldM((name, id)) {
        case ((_, parentId), childName) =>
          cfs._directories.findChild(parentId, childName)
      }
  }

  def dir(path: Path)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    if (path.filename.nonEmpty)
      Future.failed(Directory.NotDirectory(path))
    else find(path.parts).flatMap {
      case (n, i) => cfs._directories.find(i)(
        _.copy(name = n, path = this.path / path)
      )
    }

  def file(path: Path)(
    implicit cfs: CassandraFileSystem
  ): Future[File] =
    find(path.parts ++ path.filename).flatMap {
      case (n, i) => cfs._files.find(i)(
        _.copy(name = n, path = path)
      )
    }

  def file(name: String)(
    implicit cfs: CassandraFileSystem
  ): Future[File] =
    cfs._directories.findChild(id, name).flatMap {
      case (n, i) => cfs._files.find(i)(
        _.copy(name = n, path = path + name)
      )
    }

  def mkdir(name: String)(
    implicit user: User, cfs: CassandraFileSystem
  ): Future[Directory] =
    Directory(name, path / name, user.id, id).save()

  def mkdir(name: String, uid: UUID)(

    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    Directory(name, path / name, uid, id).save()

  def add(inode: INode)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.addChild(this, inode)

  def del(inode: INode)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.delChild(this, inode)

  def dir(name: String)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.findChild(id, name).flatMap {
      case (_, i) => cfs._directories.find(i)(
        _.copy(name = name, path = path / name)
      )
    }

  def dir_!(name: String)(
    implicit user: User, cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.findChild(id, name).flatMap {
      case (_, i) => cfs._directories.find(i)(
        _.copy(name = name, path = path / name)
      )
    }.recoverWith {
      case e: Directory.ChildNotFound => mkdir(name)
    }

  def delSelf(implicit cfs: CassandraFileSystem) =
    cfs._directories.find(parent).flatMap(_.del(this))

  def clear()(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] = {
    cfs._directories.stream(this) &>
      Enumeratee.mapM { inode =>
        this.del(inode).map(_ => inode)
      } |>>>
      Iteratee.foreach {
        case d: Directory => d.remove(recursive = true)
        case f: File      => f.purge()
      }
  }

  def remove(recursive: Boolean = false)(
    implicit cfs: CassandraFileSystem
  ): Future[Boolean] =
    if (recursive) for {
      _ <- clear()
      _ <- delSelf
    } yield true
    else isEmpty.andThen {
      case Success(true) => delSelf
    }

  def isEmpty(
    implicit cfs: CassandraFileSystem
  ): Future[Boolean] = cfs._directories.isEmpty(this)

}

/**
 *
 */
sealed class DirectoryTable
  extends NamedCassandraTable[DirectoryTable, Directory]
  with INodeCanonicalNamed
  with INodeKey[DirectoryTable, Directory]
  with INodeColumns[DirectoryTable, Directory]
  with DirectoryColumns[DirectoryTable, Directory] {

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

object Directory extends CanonicalNamed with ExceptionDefining {

  override val basicName: String = "dir"

  case class NotFound(id: UUID)
    extends BaseException(error_code("not.found"))

  case class ChildNotFound(parent: Any, name: String)
    extends BaseException(error_code("child.not.found"))

  case class ChildExists(parent: Any, name: String)
    extends BaseException(error_code("child.exists"))

  case class NotDirectory(path: Path)
    extends BaseException(error_code("not.dir"))

  implicit val jsonWrites = new Writes[Directory] {
    override def writes(o: Directory): JsValue = Json.obj(
      "id" -> o.id,
      "name" -> o.name,
      "path" -> o.path,
      "owner_id" -> o.owner_id,
      "created_at" -> o.created_at,
      "is_directory" -> o.is_directory,
      "is_file" -> !o.is_directory
    )
  }
}

class Directories(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder,
  val _inodes: INodes,
  val _files: Files
)
  extends DirectoryTable
  with ExtCQL[DirectoryTable, Directory]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  def findChild(
    parent: UUID, name: String
  ): Future[(String, UUID)] = CQL {
    select(_.child_id)
      .where(_.inode_id eqs parent)
      .and(_.name eqs name)
  }.one().map {
    case None     => throw Directory.ChildNotFound(parent, name)
    case Some(id) => (name, id)
  }

  /**
   * Note: After calling this method, you have to set path to correct value
   *
   * @param id inode_id
   * @param onFound will be called after inode being found
   * @return found inode
   */
  def find(id: UUID)(
    implicit onFound: Directory => Directory
  ): Future[Directory] = CQL {
    select
      .where(_.inode_id eqs id)
  }.one().map {
    case None    => throw Directory.NotFound(id)
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
    else throw Directory.ChildExists(dir.id, inode.name)
  }

  def delChild(
    dir: Directory, inode: INode
  ): Future[Directory] = CQL {
    delete
      .where(_.inode_id eqs dir.id)
      .and(_.name eqs inode.name)
  }.future().map(_ => dir)

  def renameChild(
    dir: Directory,
    inode: INode,
    newName: String
  ): Future[Boolean] = CQL {
    Batch.logged
      .add {
      delete
        .where(_.inode_id eqs dir.id)
        .and(_.name eqs inode.name)
    }
      .add {
      insert
        .value(_.inode_id, dir.id)
        .value(_.name, newName)
        .value(_.child_id, inode.id).ifNotExists()
    }
  }.future().map(_.wasApplied)

  /**
   * Write name in children list of its parent
   *
   * @param dir target Directory
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
          this.find(child_id(prs.one()))(
            _.copy(name = dir.name, path = dir.path)
          )
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
   * Stream all content in a directory
   *
   * @param dir target Directory
   * @return
   */
  def stream(dir: Directory): Enumerator[INode] = Enumerator.flatten {
    isEmpty(dir).map {
      case true  => Enumerator[INode]()
      case false =>
        CQL {
          select(_.name, _.child_id)
            .where(_.inode_id eqs dir.id)
        }.fetchEnumerator() &>
          Enumeratee.mapM {
            case (name, id) => _inodes.find(id).map[Option[INode]] {
              _.map { r =>
                if (_inodes.is_directory(r))
                  fromRow(r).copy(name = name, path = dir.path / name)
                else
                  _files.fromRow(r).copy(name = name, path = dir.path + name)
              }
            }
          } &> Enumeratee.flattenOption
    }
  }

  def isEmpty(dir: Directory): Future[Boolean] = CQL {
    select(_.child_id)
      .where(_.inode_id eqs dir.id)
  }.one().map(_.isEmpty).recover {
    case e: Exception => true
  }
}