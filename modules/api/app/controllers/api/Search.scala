package controllers.api

import elasticsearch._
import helpers._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Controller
import security.PermissionCheckable

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Search
  extends Controller
  with ExHeaders with PermissionCheckable {

  override val moduleName = "search"

  def index(types: Seq[String], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      val indexTypes = types.distinct
      
      val defs = indexTypes.zip(p / indexTypes.size).flatMap {
        case (Users.moduleName, _p)  =>
          Some((es: ES) => es.Search(q, _p) in User)
        case (Groups.moduleName, _p) =>
          Some((es: ES) => es.Search(q, _p) in Group)
        case _                       => None
      }

      if (defs.isEmpty)
        Future.successful(Ok(Json.arr()))
      else
        ES.Multi(defs: _*).future()
          .map(PageMSResp(p, _)).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.Search.index(types, q, _))
          )
        }
    }
}