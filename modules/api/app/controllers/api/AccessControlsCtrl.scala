package controllers.api

import java.util.UUID

import elasticsearch._
import helpers._
import models.AccessControl.NotFound
import models._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
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
  extends Secured(AccessControlsCtrl)
  with Controller
  with BasicPlayComponents
  with I18nSupport
  with LinkHeader
  with Logging {

  def index(q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      (ES.Search(q, p) in AccessControl future()).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.AccessControlsCtrl.index(q, _))
        )
      }
    }

  def show(id: UUID, res: String, act: String) =
    PermCheck(_.Show).async { implicit req =>
      accessControlRepo.find(id, res, act).map { ac =>
        Ok(Json.toJson(ac))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    PermCheck(_.Create).async { implicit req =>
      BindJson().as[AccessControl] { success =>
        accessControlRepo.find(success).map { found =>
          Ok(Json.toJson(found))
        }.recoverWith { case e: NotFound =>
          (for {
            exists <-
            if (!success.is_group) User.exists(success.principal)
            else groupRepo.exists(success.principal)

            saved <- success.save
            _resp <- ES.Index(saved) into AccessControl
          } yield (saved, _resp)).map { case (saved, _resp) =>

            Created(_resp._1)
              .withHeaders(
                LOCATION -> routes.AccessControlsCtrl.show(
                  saved.principal, saved.resource, saved.action
                ).url
              )
          }.recover {
            case e: models.User.NotFound => BadRequest(JsonMessage(e))
            case e: Group.NotFound       => BadRequest(JsonMessage(e))
          }
        }
      }
    }

  def destroy(id: UUID, res: String, act: String) =
    PermCheck(_.Destroy).async { implicit req =>
      (for {
        __ <- ES.Delete(AccessControl.genId(res, act, id)) from AccessControl
        ac <- accessControlRepo.find(id, res, act)
        __ <- accessControlRepo.remove(id, res, act)
      } yield res).map { _ =>
        NoContent
      }.recover {
        case e: AccessControl.NotFound => NotFound
      }
    }

  def save(id: UUID, res: String, act: String) =
    PermCheck(_.Save).async { implicit req =>
      BindJson().as[AccessControl] { ac =>
        (for {
          _____ <- accessControlRepo.find(id, res, act)
          saved <- ac.save
          _resp <- ES.Update(saved) in AccessControl
        } yield _resp._1).map {
          Ok(_)
        }.recover {
          case e: BaseException => NotFound
        }

      }
    }

  def dropIndexIfEmpty: Future[Boolean] = for {
    _empty <- accessControlRepo.isEmpty
    result <-
    if (_empty) {
      Logger.info(s"Clean elasticsearch index $basicName")
      (ES.Delete from AccessControl).map(_ => true)
    }
    else
      Future.successful(false)
  } yield result
}

object AccessControlsCtrl extends Secured(AccessControl)