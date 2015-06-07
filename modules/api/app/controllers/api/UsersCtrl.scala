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

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class UsersCtrl(
  val basicPlayApi: BasicPlayApi,
  val ES: ElasticSearch
)(
  implicit
  val accessControlRepo: AccessControls,
  val _users: Users,
  val rateLimitRepo: RateLimits,
  val _groups: Groups
)
  extends Secured(UsersCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with InternalGroupsComponents
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
        (ES.Search(q, p) in _users future()).map { page =>
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
          _resp <- ES.Index(saved) into _users
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

  def dropIndexIfEmpty: Future[Boolean] = for {
    _empty <- _users.isEmpty
    result <-
    if (_empty) {
      Logger.info(s"Clean elasticsearch index $basicName")
      (ES.Delete from _users).map(_ => true)
    }
    else
      Future.successful(false)
  } yield result

}

object UsersCtrl extends Secured(User)