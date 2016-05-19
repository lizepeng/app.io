package controllers.api_internal

import java.util.UUID

import controllers.RateLimitConfigComponents
import elasticsearch._
import helpers._
import models._
import models.misc._
import models.sys.SysConfigs
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import protocols._
import security._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _sysConfig: SysConfigs,
  val _groups: Groups
) extends GroupCanonicalNamed
  with CheckedModuleName
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionRequiredComponents
  with UserActionComponents[GroupsCtrl.AccessDef]
  with GroupsCtrl.AccessDef
  with ExceptionHandlers
  with DefaultPlayExecutor
  with RateLimitConfigComponents
  with I18nSupport
  with Logging {

  case class GroupInfo(group_name: Name, description: Option[String])

  object GroupInfo {implicit val jsonFormat = Json.format[GroupInfo]}

  def index(ids: Seq[UUID], q: Option[String], p: Pager, sort: Seq[SortField]) =
    UserAction(_.P03).async { implicit req =>
      if (ids.nonEmpty)
        _groups.find(ids).map { grps =>
          Ok(Json.toJson(grps))
        }
      else
        (es.Search(q, p, sort, Some(false)) in _groups future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.GroupsCtrl.index(Nil, q, _, sort))
          )
        }
    }

  def show(id: UUID) =
    UserAction(_.P02).async { implicit req =>
      _groups.find(id).flatMap {
        HttpCaching { grp =>
          Ok(Json.toJson(grp))
        }
      }
    }

  def create =
    UserAction(_.P01).async { implicit req =>
      BindJson().as[GroupInfo].async { success =>
        for {
          saved <- _groups.save(Group(group_name = success.group_name, description = success.description))
          _resp <- es.Index(saved) into _groups
        } yield {
          Created(_resp._1).withHeaders(
            LOCATION -> routes.GroupsCtrl.show(saved.id).url
          )
        }
      }()
    }

  def destroy(id: UUID) =
    UserAction(_.P06).async { implicit req =>
      (for {
        grp <- _groups.find(id)
        ___ <- _groups.remove(id)
        ___ <- es.Delete(id) from _groups
      } yield {
        NoContent
      }).recover {
        case e: Group.NotWritable => MethodNotAllowed(JsonMessage(e))
        case e: Group.NotEmpty    => MethodNotAllowed(JsonMessage(e))
      }
    }

  def checkName =
    UserAction(_.P02).async { implicit req =>
      BodyIsJsObject { obj =>
        Reads.at[Name](__ \ "group_name").reads(obj).fold(
          failure => UnprocessableEntity(JsonClientErrors(failure)),
          success => Ok
        )
      }
    }

  def save(id: UUID) =
    UserAction(_.P05).async { implicit req =>
      BindJson().as[GroupInfo].async { grp =>
        for {
          group <- _groups.find(id)
          saved <- _groups.save(group.copy(group_name = grp.group_name, description = grp.description))
          _resp <- es.Update(saved) in _groups
        } yield {
          Ok(_resp._1)
        }
      }()
    }

  def users(id: UUID, pager: Pager) =
    UserAction(_.P16).async { implicit req =>
      for {
        page <- _groups.children(id, pager)
        usrs <- _users.find(page)
      } yield {
        Ok(Json.toJson(usrs)).withHeaders(
          linkHeader(page, routes.GroupsCtrl.users(id, _))
        )
      }
    }

  def addUser(id: UUID) =
    UserAction(_.P17).async { implicit req =>
      BodyIsJsObject.async { obj =>
        def u1 = Reads.at[UUID](__ \ "id").reads(obj).map(_users.find)
        def u2 = Reads.at[EmailAddress](__ \ "email").reads(obj).map(_users.find)
        (u1 orElse u2).fold(
          failure => Future.successful {
            UnprocessableEntity(JsonClientErrors(failure))
          },
          success => success.flatMap { user =>
            if (user.groups.contains(id)) Future.successful {
              Ok(Json.toJson(user))
            }
            else _groups.addChild(id, user.id).map { _ =>
              Created(Json.toJson(user))
            }
          }
        )
      }
    }

  def delUser(id: UUID, uid: UUID) =
    UserAction(_.P18).async { implicit req =>
      _groups.delChild(id, uid).map { _ => NoContent }
    }

  def layouts(ids: Seq[UUID]) =
    UserAction(_.P19).async { implicit req =>
      _groups.findLayouts(ids).map { layouts =>
        val map = layouts.collect {
          case (gid, Some(layout)) => gid.toString -> layout
        }.toMap
        Ok(Json.toJson(map))
      }
    }

  def setLayout(gid: UUID) =
    UserAction(_.P20).async { implicit req =>
      BindJson().as[Layout].async { success =>
        _groups.setLayout(gid, success).map { saved =>
          Ok(Json.toJson(saved))
        }
      }()
    }
}

object GroupsCtrl
  extends GroupCanonicalNamed
    with PermissionCheckable {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    /** Index Users */
    val P16 = Access.Pos(16)
    /** Add User */
    val P17 = Access.Pos(17)
    /** Remove User */
    val P18 = Access.Pos(18)
    /** Index Layouts */
    val P19 = Access.Pos(19)
    /** Save Layouts */
    val P20 = Access.Pos(20)

    def values = Seq(P01, P02, P03, P05, P06, P16, P17, P18, P19, P20)
  }

  object AccessDef extends AccessDef
}