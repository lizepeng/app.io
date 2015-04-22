package controllers.api

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.ExHeaders
import helpers._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

import scala.concurrent.Future

//* TODO authorization
//* TODO Rate limit
//* TODO ETag

/**
 * @author zepeng.li@gmail.com
 */
object Groups
  extends MVController(Group)
  with ExHeaders with AppConfig {

  def index(pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      Group.list(pager).map { page =>
        Ok(Json.toJson(page.elements))
          .withHeaders(linkHeader(page, routes.Groups.index))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      Group.find(id).map { grp =>
        Ok(Json.toJson(grp))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    PermCheck(_.Save).async(parse.json) { implicit req =>
      req.body match {
        case json: JsObject =>
          (json ++ Json.obj(
            "id" -> UUIDs.timeBased(),
            "is_internal" -> false
          )).validate[Group].fold(
              failure => Future.successful(
                UnprocessableEntity(JsonClientErrors(failure))
              ),
              success => success.save.map { saved =>
                Created(Json.toJson(saved))
                  .withHeaders(LOCATION -> routes.Groups.show(saved.id).url)
              }
            )
        case _              => Future.successful(
          BadRequest(WrongTypeOfJSON())
        )
      }

    }

  def destroy(id: UUID) =
    PermCheck(_.Destroy).async { implicit req =>
      Group.remove(id).map {
        _ => NoContent
      }.recover {
        case e: Group.NotWritable =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotEmpty    =>
          MethodNotAllowed(JsonMessage(e))
      }
    }

  def save(id: UUID) =
    PermCheck(_.Save).async(parse.json) { implicit req =>
      req.body.validate[Group].fold(
        failure => Future.successful(
          UnprocessableEntity(JsonClientErrors(failure))
        ),
        success =>
          (for {
            ___ <- Group.find(id)
            grp <- success.save
          } yield grp).map { grp =>
            Ok(Json.toJson(grp))
          }.recover {
            case e: BaseException => NotFound
          }
      )
    }

  def users(id: UUID, pager: Pager) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        page <- Group.children(id, pager)
        usrs <- User.find(page.elements.toList)
      } yield (page, usrs.values)).map { case (page, usrs) =>
        Ok(Json.toJson(usrs.map(_.toUserInfo)))
          .withHeaders(linkHeader(page, routes.Groups.users(id, _)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def addUser(id: UUID) =
    PermCheck(_.Save).async(parse.json) { implicit req =>
      (req.body \ "id").validate[UUID].map(User.find)
        .orElse(
          (req.body \ "email").validate[String].map(User.find)
        ).map {
        _.flatMap { user =>
          val info: UserInfo = user.toUserInfo
          if (user.groups.contains(id)) Future.successful {
            Ok(Json.toJson(info))
          }
          else Group.addChild(id, user.id).map { _ =>
            Created(Json.toJson(info))
          }
        }
      }.fold(
          failure => Future.successful {
            UnprocessableEntity(JsonClientErrors(failure))
          },
          success => success
        )
        .recover {
        case e: User.NotFound => NotFound(JsonMessage(e))
      }
    }

  def delUser(id: UUID, uid: UUID) =
    PermCheck(_.Destroy).async { implicit req =>
      Group.delChild(id, uid).map { _ => NoContent }
    }
}