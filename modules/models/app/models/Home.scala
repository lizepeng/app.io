package models

import models.cfs.{CFS, Directory}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Home {

  def apply(implicit user: User): Future[Directory] = {
    Directory.find(user.id).recoverWith {
      case Directory.NotFound(id) =>
        CFS.root.flatMap { root =>
          Directory(id, root.id, user.id).save()
        }
    }
  }

  def temp(implicit user: User): Future[Directory] = {
    Home(user).flatMap(_.dir_!("temp"))
  }
}