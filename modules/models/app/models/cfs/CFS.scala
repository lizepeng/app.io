package models.cfs

import com.websudos.phantom.Implicits._
import helpers._
import models._
import models.cfs.Directory.NotFound
import models.sys.SysConfig
import play.api.Play.current

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object CFS extends ModuleLike with SysConfig with AppConfig {

  override val moduleName = "files"

  val streamFetchSize = fetchSize("stream")
  val listFetchSize   = fetchSize("list")

  lazy val root: Future[Directory] =
    getUUID("root").flatMap { id =>
      Directory.find(id).recoverWith {
        case ex: NotFound => for {
          ur <- User.root
          fr <- Directory("/", Path.root, ur.id, id, id).save()
          __ <- fr.mkdir("tmp", ur.id)
        } yield fr
      }
    }
}