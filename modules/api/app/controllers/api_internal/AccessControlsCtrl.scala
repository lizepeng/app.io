package controllers.api_internal

import java.util.UUID

import controllers.RateLimitConfigComponents
import elasticsearch._
import helpers._
import models._
import models.misc._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import protocols._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _groups: Groups
) extends AccessControlCanonicalNamed
  with CheckedModuleName
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents[AccessControlsCtrl.AccessDef]
  with AccessControlsCtrl.AccessDef
  with DefaultPlayExecutor
  with RateLimitConfigComponents
  with I18nSupport
  with Logging {

  def index(q: Option[String], p: Pager, sort: Seq[SortField]) =
    UserAction(_.P03).async { implicit req =>
      (es.Search(q, p, sort) in _accessControls future()).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.AccessControlsCtrl.index(q, _, sort))
        )
      }
    }

  def show(principal_id: UUID, resource: String) =
    UserAction(_.P02).async { implicit req =>
      _accessControls.find(principal_id, resource).map { ace =>
        Ok(Json.toJson(ace))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    UserAction(_.P01).async { implicit req =>
      BindJson.and(
        Json.obj("permission" -> 0L)
      ).as[AccessControlEntry] { success =>
        _accessControls.find(success).map { found =>
          Ok(Json.toJson(found))
        }.recoverWith {
          case e: AccessControlEntry.NotFound =>
            (for {
              exists <-
              if (!success.is_group) _users.exists(success.principal_id)
              else _groups.exists(success.principal_id)

              saved <- success.save
              _resp <- es.Index(saved) into _accessControls
            } yield (saved, _resp)).map { case (saved, _resp) =>

              Created(_resp._1)
                .withHeaders(
                  LOCATION -> routes.AccessControlsCtrl.show(
                    saved.principal_id, saved.resource
                  ).url
                )
            }.recover {
              case e: User.NotFound  => BadRequest(JsonMessage(e))
              case e: Group.NotFound => BadRequest(JsonMessage(e))
            }
        }
      }
    }

  def destroy(principal_id: UUID, resource: String) =
    UserAction(_.P06).async { implicit req =>
      (for {
        ___ <- es.Delete(AccessControlEntry.genId(resource, principal_id)) from _accessControls
        ace <- _accessControls.find(principal_id, resource)
        ___ <- _accessControls.remove(principal_id, resource)
      } yield ace).map { _ =>
        NoContent
      }.recover {
        case e: AccessControlEntry.NotFound => NotFound
      }
    }

  def toggle(principal_id: UUID, resource: String, pos: Int) =
    UserAction(_.P05).async { implicit req =>
      (for {
        found <- _accessControls.find(principal_id, resource)
        saved <- found.copy(permission = found.permission ^ (1L << pos)).save
        _resp <- es.Update(saved) in _accessControls
      } yield _resp._1).map {
        Ok(_)
      }.recover {
        case e: BaseException => NotFound
      }
    }
}

object AccessControlsCtrl
  extends AccessControlCanonicalNamed
    with PermissionCheckable {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    def values = Seq(P01, P02, P03, P05, P06)
  }

  object AccessDef extends AccessDef
}