package models.cfs

import com.websudos.phantom.dsl._
import helpers._
import models._
import models.cfs.Directory.NotFound
import models.sys.SysConfig

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object CFS
  extends CanonicalNamed
  with SysConfig {

  override val basicName = "files"

  lazy val root: Future[Directory] =
    System.UUID("root").flatMap { id =>
      Directory.find(id).recoverWith {
        case ex: NotFound => for {
          ur <- User.root
          fr <- Directory("/", Path.root, ur.id, id, id).save()
          __ <- fr.mkdir("tmp", ur.id)
        } yield fr
      }
    }
}