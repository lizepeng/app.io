package models

import helpers.AuthCheck
import models.cfs.{CFS, Directory}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Home {
  def apply(user: User): Future[Directory] = {
    Directory.find(user.id).recoverWith {
      case Directory.NotFound(id) =>
        Directory(id, CFS.root.id, user.id).save()
    }
  }

  def apply(user: Option[User]): Future[Directory] = {
    apply(user.getOrElse(throw AuthCheck.Unauthorized()))
  }
}