package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import common.AppConfig
import models.cfs.Block.BLK
import models.sys.SysConfig
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object CFS extends AppConfig {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  val cfg_key = "fact.models.cfs"

  lazy val root: Directory = Await.result(
  {
    def newRoot(id: UUID): Directory = {
      Directory(INode(id = id, name = "/", parent = id, is_directory = true))
    }

    SysConfig.get(cfg_key, _.cfs_root).flatMap {
      case Some(id) => dir.find(id).flatMap {
        case None      => dir.save(newRoot(id))
        case d@Some(_) => Future.successful(d)
      }
      case None     => {
        val id: UUID = UUIDs.timeBased()
        SysConfig.put(cfg_key, DateTime.now(), (_.cfs_root setTo id))
        dir.save(newRoot(id))
      }
    }

  }, 10 seconds
  ).get

  val fetchSize = config.getInt(s"$cfg_key.fetch-size").getOrElse(2000)

  object file {
    def find(id: UUID): Future[Option[File]] = {
      INode.find(id).map(_.map(File(_)))
    }

    def read(file: File): Enumerator[BLK] = {
      INode.read(file)
    }

    def read(file: File, from: Int): Enumerator[BLK] = {
      INode.read(file, from)
    }

    def save(file: File): Iteratee[BLK, File] = {
      INode.streamWriter(file.inode).map(File(_))
    }

    def remove(id: UUID): Future[ResultSet] = {
      INode.remove(id)
    }

    def list(): Future[Seq[File]] = {
      INode.all().map(_.filter(!_.is_directory).map(File(_)))
    }
  }

  object dir {

    def find(id: UUID) = {
      INode.find(id).map(_.map(Directory(_)))
    }

    def save(dir: Directory): Future[Option[Directory]] = {
      INode.write(dir.inode).flatMap(_ => find(dir.id))
    }
  }

}

case class File(inode: INode)

object File {
  implicit def fileToINode(f: File): INode = f.inode

  def apply(name: String, parent: Directory): File = {
    File(INode(name = name, parent = parent.id))
  }
}

case class Directory(inode: INode) {
  def isRoot = this == CFS.root

  val is_directory: Boolean = true
}

object Directory {
  implicit def directoryToINode(d: Directory): INode = d.inode

  def apply(name: String, parent: UUID): Directory = {
    Directory(INode(name = name, parent = parent))
  }
}