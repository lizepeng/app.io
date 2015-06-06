package models.cfs

import com.websudos.phantom.dsl._
import helpers._
import models.sys.{SysConfig, SysConfigs}

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
//TODO
object CFS
  extends CanonicalNamed {

  override val basicName = "files"
}

class CFS(
  implicit
  val sysConfig: SysConfigs,
  val iNodeRepo: INodes,
  val basicPlayApi: BasicPlayApi
)
  extends CanonicalNamed
  with SysConfig {

  override val basicName = "files"

  val Directory: Directories = new Directories

  //TODO
  lazy val root: Future[Directory] =
    System.UUID("root").flatMap { id =>
      Directory.find(id)
      //        .recoverWith {
      //        case ex: NotFound => for {
      //          ur <- User.root
      //          fr <- Directory("/", Path.root, ur.id, id, id).save()
      //          __ <- fr.mkdir("tmp", ur.id)
      //        } yield fr
      //      }
    }
}