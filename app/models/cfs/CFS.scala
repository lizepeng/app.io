package models.cfs

import com.websudos.phantom.Implicits._
import helpers._
import models.User
import models.cfs.Directory.NotFound
import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object CFS extends ModuleLike with SysConfig with AppConfig {

  override val moduleName = "files"

  val streamFetchSize = fetchSize("stream")
  val listFetchSize   = fetchSize("list")

  lazy val root: Directory = Await.result(
    getUUID("root").flatMap { id =>
      Directory.find(id).recoverWith {
        case ex: NotFound =>
          Directory(id, id, User.root, name = "/").save()
      }
    }
    , 10 seconds
  )
}