package models.cfs

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.sys.{SysConfig, SysConfigs}
import models.{User, Users}

import scala.concurrent.Future

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
  with SysConfig {

  implicit val _blocks         = new Blocks
  implicit val _indirectBlocks = new IndirectBlocks
  implicit val _files          = new Files
  implicit val _inodes         = new INodes
  implicit val _directories    = new Directories

  def home(implicit user: User): Future[Directory] = {
    _directories.find(user.id)(
      _.copy(
        name = user.id.toString,
        path = Path.root / user.id.toString
      )
    ).recoverWith {
      case Directory.NotFound(id) =>
        root.flatMap { root =>
          Directory(
            id.toString,
            Path.root / id.toString,
            user.id,
            root.id,
            id
          ).save()(this)
        }
    }
  }

  def temp(implicit user: User): Future[Directory] = {
    home(user).flatMap(_.dir_!("temp")(user, this))
  }

  lazy val root: Future[Directory] =
    System.UUID("root").flatMap { id =>
      _directories.find(id)
        .recoverWith {
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

object CassandraFileSystem extends CassandraFileSystemCanonicalNamed