package models.cfs

import com.websudos.phantom.dsl._
import helpers._
import models.sys.{SysConfig, SysConfigs}
import models.{User, Users}

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
class CassandraFileSystem(
  implicit
  val _basicPlayApi: BasicPlayApi,
  val _users: Users,
  val _sysConfig: SysConfigs
)
  extends CassandraFileSystemCanonicalNamed
  with BasicPlayComponents
  with SysConfig {

  val _blocks         = new Blocks
  val _indirectBlocks = new IndirectBlocks(_basicPlayApi, _blocks)
  val _files          = new Files(_basicPlayApi, _blocks, _indirectBlocks)
  val _inodes         = new INodes(_basicPlayApi)
  val _directories    = new Directories(_basicPlayApi, _inodes)

  lifecycle.addStopHook { () =>
    Future.sequence(
      Seq(
        Future.successful(_blocks.shutdown()),
        Future.successful(_indirectBlocks.shutdown()),
        Future.successful(_files.shutdown()),
        Future.successful(_inodes.shutdown()),
        Future.successful(_directories.shutdown())
      )
    ).map(_ => Unit)
  }

  def home(implicit user: User): Future[Directory] = {
    _directories.find(user.id).recoverWith {
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

  //TODO
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

  override val basicName = "files"
}

object CassandraFileSystem extends CassandraFileSystemCanonicalNamed