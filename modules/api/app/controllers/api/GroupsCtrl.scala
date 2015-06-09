package controllers.api

import java.util.UUID

import batches.ReIndex
import com.datastax.driver.core.utils.UUIDs
import elasticsearch._
import helpers._
import models._
import models.json._
import play.api.i18n._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._

import scala.concurrent.Future

//* TODO authorization

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val permCheckRequired: PermCheckRequired,
  val es: ElasticSearch,
  val _groups: Groups
)
  extends Secured(GroupsCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with PermCheckComponents
  with DefaultPlayExecutor
  with I18nSupport
  with Logging {

  ESIndexCleaner(_groups).dropIndexIfEmpty

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        _groups.find(ids).map { grps =>
          Ok(Json.toJson(grps))
        }
      else
        (es.Search(q, p) in _groups future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.GroupsCtrl.index(Nil, q, _))
          )
        }
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      _groups.find(id).map {
        NotModifiedOrElse { grp =>
          Ok(Json.toJson(grp))
        }
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    PermCheck(_.Save).async { implicit req =>
      BindJson.and(
        Json.obj(
          "id" -> UUIDs.timeBased(),
          "is_internal" -> false
        )
      ).as[Group] {
        success => for {
          saved <- success.save
          _resp <- es.Index(saved) into _groups
        } yield
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.GroupsCtrl.show(saved.id).url
            )
      }
    }

  def destroy(id: UUID) =
    PermCheck(_.Destroy).async { implicit req =>
      (for {
        ___ <- es.Delete(id) from _groups
        grp <- _groups.find(id)
        ___ <- _groups.remove(id)
      } yield grp).map { _ =>
        NoContent
      }.recover {
        case e: Group.NotWritable =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotEmpty    =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotFound    =>
          NotFound
      }
    }

  def save(id: UUID) =
    PermCheck(_.Save).async { implicit req =>
      BindJson().as[Group] { grp =>
        (for {
          _____ <- _groups.find(id)
          saved <- grp.save
          _resp <- es.Update(saved) in _groups
        } yield _resp._1).map {
          Ok(_)
        }.recover {
          case e: BaseException => NotFound
        }
      }
    }

  def users(id: UUID, pager: Pager) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        page <- _groups.children(id, pager)
        usrs <- _users.find(page)
      } yield (page, usrs)).map { case (page, usrs) =>
        Ok(JsArray(usrs.map(_.toJson)))
          .withHeaders(linkHeader(page, routes.GroupsCtrl.users(id, _)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def addUser(id: UUID) =
    PermCheck(_.Save).async { implicit req =>
      BodyIsJsObject { obj =>
        obj.validate[UUID]((__ \ 'id).read[UUID]).map(_users.find)
          .orElse(
            obj.validate[String]((__ \ 'email).read[String]).map(_users.find)
          ).map {
          _.flatMap { user =>
            if (user.groups.contains(id)) Future.successful {
              Ok(user.toJson)
            }
            else _groups.addChild(id, user.id).map { _ =>
              Created(user.toJson)
            }
          }
        }.fold(
            failure => Future.successful {
              UnprocessableEntity(JsonClientErrors(failure))
            },
            success => success
          )
          .recover {
          case e: models.User.NotFound => NotFound(JsonMessage(e))
        }
      }
    }

  def delUser(id: UUID, uid: UUID) =
    PermCheck(_.Destroy).async { implicit req =>
      _groups.delChild(id, uid).map { _ => NoContent }
    }

  def reindex: Future[Boolean] = {
    new ReIndex[Group](
      _groups.all,
      list => (es.BulkIndex(list) into _groups)
        .map { res => Logger.info(res.getTook.toString) }
    )(10).start().map(_ => true)
  }
}

object GroupsCtrl extends Secured(Group)