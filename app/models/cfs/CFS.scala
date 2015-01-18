package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import common.AppConfig
import models.cassandra.Cassandra
import models.cfs.Block._
import models.sys.SysConfig
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object CFS extends AppConfig with Cassandra {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  val config_key      = "fact.models.cfs"
  val streamFetchSize = config.getInt("stream-fetch-size").getOrElse(2000)
  val listFetchSize   = config.getInt("list-fetch-size").getOrElse(2000)

  Await.result(INode.create.future(), 500 millis)
  Await.result(IndirectBlock.create.future(), 500 millis)
  Await.result(Block.create.future(), 500 millis)

  lazy val root: Directory = Await.result(
  {
    def newRoot(id: UUID): Directory = Directory(id, id, name = "/")

    SysConfig.get(config_key, _.cfs_root).flatMap {
      case Some(id) => dir.find(id).map {
        case None    => dir.save(newRoot(id))
        case Some(d) => d
      }
      case None     => Future.successful {
        val id: UUID = UUIDs.timeBased()
        SysConfig.put(config_key, DateTime.now(), _.cfs_root setTo id)
        dir.save(newRoot(id))
      }
    }

  }, 10 seconds
  )

  def find(path: Path): Future[Option[Either[Directory, File]]] =
    (Enumerator(path.dirs: _*) |>>>
      Iteratee.foldM[String, Option[Directory]](Some(CFS.root)) {
        (directory, sub) => directory match {
          case None      => Future.successful(None)
          case Some(dir) => dir.find(sub)
        }
      }).flatMap {
      case None      => Future.successful(None)
      case Some(dir) => path.filename match {
        case Some(filename) => dir.findFile(filename).map(_.map(Right(_)))
        case None           => Future.successful(Some(Left(dir)))
      }
    }

  object file {

    def find(id: UUID): Future[Option[File]] = File.findBy(id)

    def read(file: File): Enumerator[BLK] = IndirectBlock.read(file)

    def read(file: File, offset: Long): Enumerator[BLK] = {
      IndirectBlock.read(file, offset)
    }

    def save(file: File): Iteratee[BLK, File] = File.streamWriter(file)

    def purge(id: UUID): Future[ResultSet] = File.purge(id)
  }

  object dir {
    def find(id: UUID): Future[Option[Directory]] = Directory.findBy(id)

    def save(dir: Directory): Directory = Directory.write(dir: Directory)
  }

}