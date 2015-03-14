package models.cfs

import com.websudos.phantom.Implicits._
import helpers.{AppConfig, Logging}
import models.cassandra.Cassandra
import models.cfs.Directory.NotFound
import models.security.Permission
import models.sys.SysConfig
import models.{Group, User}
import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object CFS extends Cassandra with Logging with SysConfig with AppConfig {

  override val module_name: String = "fact.module.cfs"
  val streamFetchSize = config.getInt("stream-fetch-size").getOrElse(2000)
  val listFetchSize   = config.getInt("list-fetch-size").getOrElse(2000)

  lazy val root: Directory = Await.result(
    getUUID("root").flatMap {
      id => Directory.find(id).recoverWith {
        case ex: NotFound => Directory(id, id, User.root, name = "/").save()
      }
    }
    , 10 seconds
  )

  trait FilePerms extends Permission[Group, String, INode]

}