package controllers.api

import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Controller
import security.PermissionCheckable

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class Search(
  val basicPlayApi: BasicPlayApi
)
  extends Controller
  with LinkHeader
  with PermissionCheckable
  with BasicPlayComponents
  with I18nSupport {

  def index(types: Seq[String], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      val indexTypes = types.distinct

      val defs = indexTypes.zip(p / indexTypes.size).flatMap {
        case (User.moduleName, _p)  =>
          Some((es: ES) => es.Search(q, _p) in User)
        case (Group.moduleName, _p) =>
          Some((es: ES) => es.Search(q, _p) in Group)
        case _                      => None
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

object Search extends PermissionCheckable {

  override val moduleName = "search"
}