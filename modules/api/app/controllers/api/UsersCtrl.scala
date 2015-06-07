package controllers.api

import java.util.UUID

import elasticsearch.ElasticSearch
import helpers._
import models._
import models.json._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._

/**
 * @author zepeng.li@gmail.com
 */
class UsersCtrl(
  implicit
  val _basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val _groups: Groups,
  val _es: ElasticSearch
)
  extends Secured(UsersCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with InternalGroupsComponents
  with PermCheckComponents
  with I18nSupport
  with Logging {

  def groups(id: UUID, options: Option[String]) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        user <- _users.find(id)
        grps <- _groups.find(
          options match {
            case Some("internal") => user.internal_groups
            case Some("external") => user.external_groups
            case _                => user.groups
          }
        )
      } yield grps).map { grps =>
        Ok(Json.toJson(grps.filter(_.id != _internalGroups.AnyoneId)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        _users.find(ids).map { usrs =>
          Ok(JsArray(usrs.map(_.toJson)))
        }
      else
        (_es.Search(q, p) in _users future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.UsersCtrl.index(Nil, q, _))
          )
        }
    }

  def create =
    PermCheck(_.Save).async { implicit req =>
      BindJson().as[JsUser] {
        success => (for {
          saved <- success.toUser.save
          _resp <- _es.Index(saved) into _users
        } yield (saved, _resp)).map { case (saved, _resp) =>
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.GroupsCtrl.show(saved.id).url
            )
        }.recover {
          case e: models.User.EmailTaken => MethodNotAllowed(JsonMessage(e))
        }
      }
    }

}

object UsersCtrl extends Secured(User)