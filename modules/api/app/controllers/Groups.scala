package controllers.api

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.ExHeaders
import helpers._
import models.Group
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
      Group.remove(id)
        .map { _ => NoContent }
        .recover { case e: Group.NotWritable => NoContent }
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
}