package controllers.api

import java.util.UUID

import controllers.ExHeaders
import helpers._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Users
  extends MVController(User)
  with ExHeaders with AppConfig {

  def groups(id: UUID, options: Option[String]) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        user <- User.find(id)
        gids <- options match {
          case Some("internal") => Future.successful {
            Nil ::: user.internal_groups
          }
          case Some("external") => user.external_groups
          case _                => user.groups
        }
        grps <- Group.find(gids)
      } yield grps.values).map { grps =>
        Ok(Json.toJson(grps.filter(_.id != InternalGroups.AnyoneId)))
      }.recover {
        case e: BaseException => NotFound
      }
    }
}