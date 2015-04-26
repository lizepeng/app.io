package controllers.api

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import elasticsearch._
import helpers._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object AccessControls
  extends SecuredController(Group)
  with ExHeaders {

  def index(q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      (ES.Search(q, p) in AccessControl).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.Groups.index(Nil, q, _))
        )
      }
    }

  def show(principal_id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      AccessControl.find(principal_id).map { grp =>
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
              success => for {
                saved <- success.save
                _resp <- ES.Index(saved) into Group
              } yield {
                  Created(_resp._1)
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
      (for {
        grp <- Group.find(id)
        ___ <- Group.remove(id)
        res <- ES.Delete(grp) from Group
      } yield res).map { _ =>
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
    PermCheck(_.Save).async(parse.json) { implicit req =>
      req.body.validate[Group].fold(
        failure => Future.successful(
          UnprocessableEntity(JsonClientErrors(failure))
        ),
        success =>
          (for {
            _____ <- Group.find(id)
            saved <- success.save
            _resp <- ES.Update(saved) in Group
          } yield _resp._1).map {
            Ok(_)
          }.recover {
            case e: BaseException => NotFound
          }
      )
    }
}