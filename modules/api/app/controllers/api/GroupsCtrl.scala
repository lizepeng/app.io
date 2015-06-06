package controllers.api

import java.util.UUID

import batches.ReIndex
import com.datastax.driver.core.utils.UUIDs
import elasticsearch._
import helpers._
import models._
import models.json._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._

import scala.concurrent.Future
import scala.language.postfixOps

//* TODO authorization

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  val basicPlayApi: BasicPlayApi,
  val ES: ElasticSearch
)(
  implicit
  val accessControlRepo: AccessControls,
  val groupRepo: Groups,
  val User: Users,
  rateLimitRepo: RateLimits,
  internalGroupsRepo: InternalGroupsMapping
)
  extends Secured(GroupsCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with I18nSupport
  with Logging {

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        groupRepo.find(ids).map { grps =>
          Ok(Json.toJson(grps))
        }
      else
        (ES.Search(q, p) in Group future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.GroupsCtrl.index(Nil, q, _))
          )
        }
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      groupRepo.find(id).map {
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
          _resp <- ES.Index(saved) into Group
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
        ___ <- ES.Delete(id) from Group
        grp <- groupRepo.find(id)
        ___ <- groupRepo.remove(id)
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
          _____ <- groupRepo.find(id)
          saved <- grp.save
          _resp <- ES.Update(saved) in Group
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
        page <- groupRepo.children(id, pager)
        usrs <- User.find(page)
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
        obj.validate[UUID]((__ \ 'id).read[UUID]).map(User.find)
          .orElse(
            obj.validate[String]((__ \ 'email).read[String]).map(User.find)
          ).map {
          _.flatMap { user =>
            if (user.groups.contains(id)) Future.successful {
              Ok(user.toJson)
            }
            else groupRepo.addChild(id, user.id).map { _ =>
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
      groupRepo.delChild(id, uid).map { _ => NoContent }
    }

  def dropIndexIfEmpty: Future[Boolean] = for {
    _empty <- groupRepo.isEmpty
    result <-
    if (_empty) {
      Logger.info(s"Clean elasticsearch index $basicName")
      (ES.Delete from Group).map(_ => true)
    }
    else
      Future.successful(false)
  } yield result

  def reindex: Future[Boolean] = {
    new ReIndex[Group](
      groupRepo.all,
      list => (ES.BulkIndex(list) into Group)
        .map { res => Logger.info(res.getTook.toString) }
    )(10).start().map(_ => true)
  }
}

object GroupsCtrl extends Secured(Group)