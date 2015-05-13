package models

import models.cfs._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Home {

  def apply(implicit user: User): Future[Directory] = {
    Directory.find(user.id).recoverWith {
      case Directory.NotFound(id) =>
        CFS.root.flatMap { root =>
          Directory(
            id.toString,
            Path.root / id.toString,
            user.id,
            root.id,
            id
          ).save()
        }
    }
  }

  def temp(implicit user: User): Future[Directory] = {
    Home(user).flatMap(_.dir_!("temp"))
  }
}