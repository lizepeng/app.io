package models.cfs

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers.ExtString._
import helpers._
import models._
import models.cassandra._
import models.sys._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._

import scala.concurrent.Future
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
class CassandraFileSystem(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder,
  val _users: Users,
  val _sysConfig: SysConfigs
)
  extends CassandraFileSystemCanonicalNamed
  with BasicPlayComponents
  with SysConfig
  with Logging {

  implicit val _blocks        : Blocks         = new Blocks
  implicit val _indirectBlocks: IndirectBlocks = new IndirectBlocks
  implicit val _files         : Files          = new Files
  implicit val _inodes        : INodes         = new INodes
  implicit val _directories   : Directories    = new Directories
  implicit val _root          : Root           = new Root

  def home(implicit user: User): Future[Directory] = {
    val uid = user.id
    val name = uid.toString
    _directories.find(uid)(
      onFound = _.copy(name = name, path = Path.root / name)
    ).recoverWith {
      case e: Directory.NotFound => createHome
    }
  }

  def createHome(implicit user: User): Future[Directory] = {
    val uid = user.id
    val name = uid.toString
    val home = Directory(name, Path.root / name, uid, uid, uid)
    Logger.debug("Creating user home folder: " + name)
    for {
      _ <- _root.add(home.id)
      h <- home.save()(this)
    } yield h
  }

  def temp(implicit user: User): Future[Directory] = {
    home(user).flatMap(_.dir_!("temp")(user, this))
  }

  def dir(path: Path)(implicit user: User): Future[Directory] = {
    resolve(path, (dir, tail) => dir.dir(tail)(this))
  }

  def file(path: Path)(implicit user: User): Future[File] = {
    resolve(path, (dir, tail) => dir.file(tail)(this))
  }

  def inode(path: Path)(implicit user: User): Future[INode] = {
    resolve(path, (dir, tail) => dir.inode(tail)(this))
  }

  private def resolve[T](
    path: Path,
    resolver: (Directory, Path) => Future[T]
  )(implicit user: User): Future[T] = {
    path.segments.headOption match {
      case Some(head) =>
        Try(UUID.fromString(head)) match {
          case Success(id)           =>
            _directories.find(id)(
              onFound = _.copy(name = head, path = Path.root / head)
            ).recoverWith {
              case e: Directory.NotFound if id == user.id => createHome
            }.flatMap(resolver(_, path.tail))
          case Failure(e: Throwable) =>
            Logger.debug(e.getMessage)
            Future.failed(CassandraFileSystem.InvalidPath(path))
        }
      case None       =>
        Future.failed(CassandraFileSystem.InvalidPath(path))
    }
  }

  lazy val root: Future[Directory] =
    System.UUID("root").flatMap { id =>
      _directories.find(id)(
        onFound = root => root
      ).recoverWith {
        case ex: Directory.NotFound => for {
          ur <- _users.root
          fr <- Directory("/", Path.root, ur.id, id, id).save()(this)
          __ <- fr.mkdir("tmp", ur.id)(this)
        } yield fr
      }
    }

  def listRoot(pager: Pager): Future[Page[INode]] = Page(pager) {
    _root.stream &>
      Enumeratee.mapM { id =>
        val name: String = id.toString
        _directories.find(id)(
          onFound = _.copy(name = name, path = Path.root / name)
        )
      }
  }
}

trait CassandraFileSystemCanonicalNamed extends CanonicalNamed {

  override def basicName: String = "file_system"
}

object CassandraFileSystem
  extends CassandraFileSystemCanonicalNamed
  with ExceptionDefining {

  case class InvalidPath(path: Path)
    extends BaseException(error_code("invalid.path"))


  case class ExtPermission(self: Map[UUID, Permission] = Map()) extends AnyVal {

    def apply(group_id: UUID) = self.getOrElse(group_id, Permission.empty)

    def contains(group_id: UUID) = self.contains(group_id)

    def -(group_id: UUID) = self - group_id
  }

  case class Permission(self: Long) extends AnyVal {

    def |(that: Permission) = Permission(self | that.self)

    def ?(role: Roles => Role, access: Access) = {
      val want = role(Role).want(access).self
      (self & want) == want
    }

    def ^(raw: Long) = Permission(self ^ raw)

    override def toString = pprint

    def toBitSet = ExtLong.BitSet(self)

    def pprint = {
      f"${self.toBinaryString}%63s".grouped(3).toSeq.reverse.map(toRWX).mkString("|", "|", "|")
    }

    private def toRWX(code: String): String = {
      if (code.length != 3) return "???"

      def mapAt(i: Int, readable: String) = {
        val c = code.charAt(i)
        if (c == ' ' || c == '0') "-"
        else readable
      }
      s"${mapAt(0, "r")}${mapAt(1, "w")}${mapAt(2, "x")}"
    }
  }

  object Permission {

    def empty: Permission = Permission(0L)

    implicit val jsonFormat = Format(
      Reads.StringReads.map(s => s.tryToLong.getOrElse(0L)).map(Permission.apply),
      Writes[Permission](o => BinaryString.from(o.self).toJson)
    )
  }

  case class Role(pos: Long) extends AnyVal {

    def r = want(Access.r)

    def w = want(Access.w)

    def x = want(Access.x)

    def rw = want(Access.rw)

    def rx = want(Access.rx)

    def wx = want(Access.wx)

    def rwx = want(Access.rwx)

    def want(access: Access) = Permission(access.self.toLong << pos)
  }

  object Role extends Roles

  trait Roles {

    val owner = Role(0)
    val other = Role(20 * 3)

    def group(gid: Int) = if (gid < 0 || gid > 18) other else Role((1 + gid) * 3)
  }

  case class Access(self: Int = 0) extends AnyVal {

    def |(that: Access) = Access(self | that.self)

    override def toString = pprint

    def pprint = {
      val r = if ((self & 4) > 0) 'r' else '-'
      val w = if ((self & 2) > 0) 'w' else '-'
      val x = if ((self & 1) > 0) 'x' else '-'
      s"$r$w$x"
    }
  }

  object Access {

    val r   = Access(4)
    val w   = Access(2)
    val x   = Access(1)
    val wx  = Access(2 | 1)
    val rx  = Access(4 | 1)
    val rw  = Access(4 | 2)
    val rwx = Access(4 | 2 | 1)

    implicit val jsonFormat = Format(
      Reads.IntReads.map(Access.apply),
      Writes[Access](o => JsNumber(o.self))
    )
  }

}