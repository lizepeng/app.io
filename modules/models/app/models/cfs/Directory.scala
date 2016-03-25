package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers.ExtEnumeratee._
import helpers.ExtMap._
import helpers._
import models.User
import models.cassandra._
import models.cfs.Block.BLK
import models.cfs.CassandraFileSystem._
import models.misc._
import play.api.libs.iteratee.{Enumeratee => _, _}
import play.api.libs.json._

import scala.concurrent.Future
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
case class Directory(
  name: String,
  path: Path,
  owner_id: UUID,
  parent: UUID,
  id: UUID = UUIDs.timeBased(),
  permission: Permission = Role.owner.rwx,
  ext_permission: ExtPermission = ExtPermission(),
  attributes: Map[String, String] = Map(),
  is_directory: Boolean = true
) extends INode {

  /**
   * Save a file into current directory
   *
   * Note! DO NOT forget to set a permChecker
   *
   * @param fileName   if empty then use file id as its filename
   * @param permission file permission
   * @param overwrite  whether to overwrite existing file
   * @param checker    check if user have permission to overwrite existing file
   * @param user       user who is saving the file
   * @param cfs        cassandra file system
   * @return
   */
  def save(
    fileName: String = "",
    permission: Permission = Role.owner.rw,
    overwrite: Boolean = false,
    checker: PermissionChecker = alwaysBlock
  )(
    implicit user: User, cfs: CassandraFileSystem
  ): Iteratee[BLK, File] = {
    val f = File(
      name = fileName,
      path = path + fileName,
      owner_id = user.id,
      parent = id,
      permission = permission
    )
    val ff =
      if (fileName.nonEmpty) f
      else f.copy(name = f.id.toString, path = path + f.id.toString)
    Iteratee.flatten {
      addChild(ff).recoverWith {
        case e: Directory.ChildExists if overwrite =>
          for {
            old <- file(ff.name)
            ___ <- old.delete() if checker(old)
            ___ <- updateChild(ff)
          } yield Unit
      }.map { _ => ff.save() }
    }
  }

  def save()(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.write(this)

  def list(pager: Pager)(
    implicit cfs: CassandraFileSystem
  ): Future[Page[INode]] = Page(pager) {
    cfs._directories.stream(this)
  }

  private def find(segments: Seq[String])(
    implicit cfs: CassandraFileSystem
  ): Future[(String, UUID)] = {
    Enumerator(segments: _*) |>>>
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
    else find(path.segments).flatMap {
      case (n, i) => cfs._directories.find(i)(
        onFound = _.copy(name = n, path = this.path / path)
      )
    }

  def file(path: Path)(
    implicit cfs: CassandraFileSystem
  ): Future[File] =
    if (path.filename.isEmpty)
      Future.failed(File.NotFile(path))
    else find(path.segments ++ path.filename).flatMap {
      case (n, i) => cfs._files.find(i)(
        onFound = _.copy(name = n, path = this.path / path)
      )
    }

  def inode(path: Path)(
    implicit cfs: CassandraFileSystem
  ): Future[INode] = path.filename match {
    case Some(_) => file(path)
    case None    => dir(path)
  }

  def file(name: String)(
    implicit cfs: CassandraFileSystem
  ): Future[File] =
    cfs._directories.findChild(id, name).flatMap {
      case (n, i) => cfs._files.find(i)(
        onFound = _.copy(name = n, path = path + name)
      ).recoverWith {
        //remove broken link
        case e: File.NotFound => delChild(name).map(_ => throw e)
      }
    }

  def mkdir(name: String)(
    implicit user: User, cfs: CassandraFileSystem, dirPermission: Permission
  ): Future[Directory] = {
    val uid = user.id
    Directory(name, path / name, uid, id, permission = dirPermission).save()
  }

  def mkdir(name: String, uid: UUID)(
    implicit cfs: CassandraFileSystem, dirPermission: Permission
  ): Future[Directory] =
    Directory(name, path / name, uid, id, permission = dirPermission).save()

  def addChild(child: INode)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.addChild(this, child)

  def updateChild(child: INode)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.updateChild(this, child)

  def delChild(childName: String)(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] =
    cfs._directories.delChild(this, childName)

  def dir(name: String)(
    implicit cfs: CassandraFileSystem
  ): Future[Directory] =
    cfs._directories.findChild(id, name).flatMap {
      case (_, i) => cfs._directories.find(i)(
        onFound = _.copy(name = name, path = path / name)
      )
    }

  def dir_!(name: String)(
    implicit user: User, cfs: CassandraFileSystem, dirPermission: Permission
  ): Future[Directory] =
    cfs._directories.findChild(id, name).flatMap {
      case (_, i) => cfs._directories.find(i)(
        onFound = _.copy(name = name, path = path / name)
      )
    }.recoverWith {
      case e: Directory.ChildNotFound => mkdir(name)
    }

  def clear(
    checker: PermissionChecker = alwaysPass
  )(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] = {
    cfs._directories.stream(this) |>>>
      Iteratee.foreach {
        case d: Directory if checker(d) => d.delete(recursive = true, checker = checker)
        case f: File if checker(f)      => f.delete()
      }
  }

  def delete(
    recursive: Boolean = false,
    checker: PermissionChecker = alwaysPass
  )(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] =
    if (recursive) for {
      _____ <- clear(checker = checker)
      empty <- isEmpty
      _____ <- if (empty && checker(this)) delete() else delete(recursive = true)
    } yield Unit
    else isEmpty.andThen {
      case Success(true) if checker(this) => delete()
    }.map(_ => Unit)

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
      Permission(permission(r)),
      ExtPermission(ext_permission(r).mapValuesSafely(Permission(_))),
      attributes(r),
      is_directory(r)
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

  case class NotDirectory(param: Any)
    extends BaseException(error_code("not.dir"))

  implicit val jsonWrites = new Writes[Directory] {
    override def writes(o: Directory): JsValue = Json.obj(
      "id" -> o.id,
      "name" -> o.name,
      "path" -> o.path,
      "owner_id" -> o.owner_id,
      "created_at" -> o.created_at,
      "permission" -> o.permission.toBitSet.toIndices,
      "is_directory" -> o.is_directory,
      "is_file" -> !o.is_directory
    )
  }
}

class Directories(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef,
  val _inodes: INodes,
  val _files: Files
) extends DirectoryTable
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
  }.one().recover {
    case e: Throwable => Logger.trace(e.getMessage); None
  }.map {
    case None     => throw Directory.ChildNotFound(parent, name)
    case Some(id) => (name, id)
  }

  /**
   * Note: After calling this method, you have to set path to correct value
   *
   * @param id      inode_id
   * @param onFound will be called after inode being found
   * @return found inode
   */
  def find(id: UUID)(
    onFound: Directory => Directory
  ): Future[Directory] = CQL {
    select
      .where(_.inode_id eqs id)
  }.one().map {
    case None                       => throw Directory.NotFound(id)
    case Some(d) if !d.is_directory => throw Directory.NotDirectory(id)
    case Some(d)                    => onFound(d)
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

  def updateChild(
    dir: Directory, inode: INode
  ): Future[Directory] = CQL {
    update
      .where(_.inode_id eqs dir.id)
      .and(_.name eqs inode.name)
      .modify(_.child_id setTo inode.id)
  }.future().map(_ => dir)

  def delChild(
    dir: Directory, childName: String
  ): Future[Unit] = CQL {
    delete
      .where(_.inode_id eqs dir.id)
      .and(_.name eqs childName)
  }.future().map(_ => Unit)

  /**
   * Rename the child to another name
   *
   * @param dir     target dir
   * @param inode   renaming inode
   * @param newName new name for target inode
   * @return
   */
  def renameChild(
    dir: Directory,
    inode: INode,
    newName: String
  ): Future[Boolean] = CQL {
    //TODO How about if inode with target name already exists ?
    Batch.logged
      .add {
        insert
          .value(_.inode_id, dir.id)
          .value(_.name, newName)
          .value(_.child_id, inode.id).ifNotExists()
      }
      .add {
        delete
          .where(_.inode_id eqs dir.id)
          .and(_.name eqs inode.name)
      }
  }.future().map(_.wasApplied)

  /**
   * Write target dir name into children list of its parent
   *
   * If there already is a child in the parent's children list with the same name, then read that out,
   * otherwise, insert a record in the children list and then save this dir itself.
   *
   * @param dir target Directory
   * @return saved Directory
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
            onFound = _.copy(name = dir.name, path = dir.path)
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
      .value(_.permission, dir.permission.self)
      .value(_.ext_permission, dir.ext_permission.self.mapValuesSafely(_.self.toInt))
      .value(_.is_directory, true)
      .value(_.attributes, dir.attributes)
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