package controllers.api

import java.util.UUID

import elasticsearch.ES
import helpers._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object Users
  extends SecuredController(User)
  with ExHeaders with AppConfig {

  def groups(id: UUID, options: Option[String]) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        user <- User.find(id)
        grps <- Group.find(
          options match {
            case Some("internal") => Set.empty union user.int_groups
            case Some("external") => user.ext_groups
            case _                => user.groups
          }
        )
      } yield grps.values).map { grps =>
        Ok(Json.toJson(grps.filter(_.id != InternalGroups.AnyoneId)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def index(q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      (ES.Search(q, p) in User).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.Users.index(q, _))
        )
      }
    }
}