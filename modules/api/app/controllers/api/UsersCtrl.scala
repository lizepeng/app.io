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
  val groupRepo: Groups,
  val User: Users,
  val rateLimitRepo: RateLimits,
  internalGroupsRepo: InternalGroupsMapping
)
  extends Secured(UsersCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with I18nSupport
  with Logging {

  def groups(id: UUID, options: Option[String]) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        user <- User.find(id)
        grps <- groupRepo.find(
          options match {
            case Some("internal") => internalGroupsRepo.toGroupIdSet(user.int_groups)
            case Some("external") => user.ext_groups
            case _                => user.groups
          }
        )
      } yield grps).map { grps =>
        Ok(Json.toJson(grps.filter(_.id != internalGroupsRepo.AnyoneId)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        User.find(ids).map { usrs =>
          Ok(JsArray(usrs.map(_.toJson)))
        }
      else
        (ES.Search(q, p) in User future()).map { page =>
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
          _resp <- ES.Index(saved) into User
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
    _empty <- User.isEmpty
    result <-
    if (_empty) {
      Logger.info(s"Clean elasticsearch index $basicName")
      (ES.Delete from User).map(_ => true)
    }
    else
      Future.successful(false)
  } yield result

}

object UsersCtrl extends Secured(User)