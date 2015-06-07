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
  val basicPlayApi: BasicPlayApi,
  val sysConfig: SysConfigs,
  val users: Users
)
  extends CassandraFileSystemCanonicalNamed
  with SysConfig {

  val blocks         = new Blocks
  val indirectBlocks = new IndirectBlocks(basicPlayApi, blocks)
  val files          = new Files(basicPlayApi, blocks, indirectBlocks)
  val inodes         = new INodes(basicPlayApi)
  val directories    = new Directories(basicPlayApi, inodes)

  def home(implicit user: User): Future[Directory] = {
    directories.find(user.id).recoverWith {
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
      directories.find(id)
        .recoverWith {
        case ex: Directory.NotFound => for {
          ur <- users.root
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