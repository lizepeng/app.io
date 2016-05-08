package controllers.api_internal

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.RateLimitConfigComponents
import elasticsearch._
import elasticsearch.mappings.UserMapping
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
class UsersCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _groups: Groups
) extends UserCanonicalNamed
  with CheckedModuleName
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with InternalGroupsComponents
  with UserActionComponents[SearchCtrl.AccessDef]
  with SearchCtrl.AccessDef
  with DefaultPlayExecutor
  with RateLimitConfigComponents
  with BootingProcess
  with I18nSupport
  with Logging {

  onStart(es.PutMapping(UserMapping))

  case class UserInfo(
    uid: Option[UUID],
    email: EmailAddress,
    user_name: Option[UserName]
  )

  object UserInfo {implicit val jsonFormat = Json.format[UserInfo]}

  def show(id: UUID) =
    UserAction(_.P02).async { implicit req =>
      _users.find(id).map { user =>
        Ok(Json.toJson(user))
      }.recover {
        case e: User.NotFound => NotFound
      }
    }

  def groups(id: UUID, options: Option[String]) =
    UserAction(_.P16).async { implicit req =>
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

  def index(ids: Seq[UUID], q: Option[String], p: Pager, sort: Seq[SortField]) =
    UserAction(_.P03).async { implicit req =>
      if (ids.nonEmpty)
        _users.find(ids).map { usrs =>
          Ok(Json.toJson(usrs))
        }
      else
        (es.Search(q, p, sort, Some(false)) in _users future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.UsersCtrl.index(Nil, q, _, sort))
          )
        }
    }

  def create =
    UserAction(_.P01).async { implicit req =>
      BindJson().as[UserInfo] {
        success => (for {
          saved <- User(
            id = success.uid.getOrElse(UUIDs.timeBased),
            email = success.email,
            user_name = success.user_name.getOrElse(UserName.default)
          ).save
          _resp <- es.Index(saved) into _users
        } yield (saved, _resp)).map { case (saved, _resp) =>
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.GroupsCtrl.show(saved.id).url
            )
        }.recover {
          case e: User.EmailTaken => MethodNotAllowed(JsonMessage(e))
        }
      }()
    }

}

object UsersCtrl
  extends UserCanonicalNamed
    with PermissionCheckable {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    /** IndexGroups */
    val P16 = Access.Pos(16)

    def values = Seq(P01, P02, P03, P16)
  }

  object AccessDef extends AccessDef
}