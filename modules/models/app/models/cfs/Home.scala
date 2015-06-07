package models.cfs

import models.User
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class Home(
  val cfs : CFS
) {

  implicit val directory = cfs.Directory

  def apply(implicit user: User): Future[Directory] = {
    cfs.Directory.find(user.id).recoverWith {
      case Directory.NotFound(id) =>
        cfs.root.flatMap { root =>
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
    apply(user).flatMap(_.dir_!("temp"))
  }
}