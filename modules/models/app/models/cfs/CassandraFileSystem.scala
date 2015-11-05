package models.cfs

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.sys.{SysConfig, SysConfigs}
import models.{User, Users}
import play.api.libs.json.{Format, JsNumber, Reads, Writes}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

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
    Logger.debug("Creating user home folder: " + name)
    Directory(name, Path.root / name, uid, uid, uid).save()(this)
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
}

trait CassandraFileSystemCanonicalNamed extends CanonicalNamed {

  override def basicName: String = "file_system"
}

object CassandraFileSystem
  extends CassandraFileSystemCanonicalNamed
  with ExceptionDefining {

  case class InvalidPath(path: Path)
    extends BaseException(error_code("invalid.path"))


  case class Permission(self: Long) extends AnyVal {

    def |(that: Permission) = Permission(self | that.self)

    def ?(role: Roles => Role, access: Access) = {
      val want = role(Role).want(access).self
      (self & want) == want
    }

    override def toString = pprint

    def pprint = {
      f"${self.toBinaryString}%63s".grouped(3).map(toRWX).mkString("|", "|", "|")
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

    implicit val jsonFormat = Format(
      Reads.LongReads.map(Permission.apply),
      Writes[Permission](o => JsNumber(o.self))
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

    val owner = Role(20 * 3)
    val other = Role(0)

    def group(gid: Int) = if (gid < 0 || gid > 18) other else Role((19 - gid) * 3)
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