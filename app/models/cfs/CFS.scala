package models.cfs

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.Implicits._
import helpers.{AppConfig, BaseException}
import models.cassandra.Cassandra
import models.sys.SysConfig
import org.joda.time.DateTime
import play.api.Play.current

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
      case Some(id) => Directory.findBy(id).recover {
        case ex: BaseException => Directory.write(newRoot(id))
      }
      case None     => Future.successful {
        val id: UUID = UUIDs.timeBased()
        SysConfig.put(config_key, DateTime.now(), _.cfs_root setTo id)
        Directory.write(newRoot(id))
      }
    }

  }, 10 seconds
  )
}