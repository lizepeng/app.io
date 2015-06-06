package controllers.api

import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Controller

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class SearchCtrl(
  val basicPlayApi: BasicPlayApi,
  val ES: ElasticSearch
)(
  implicit
  val accessControlRepo: AccessControlRepo,
  val User: UserRepo,
  val rateLimitRepo: RateLimitRepo,
  internalGroupsRepo: InternalGroupsRepo
)
  extends Secured(SearchCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with I18nSupport {

  def index(types: Seq[String], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      val indexTypes = types.distinct

      val defs = indexTypes.zip(p / indexTypes.size).flatMap {
        case (User.`basicName`, _p)  =>
          Some((es: ElasticSearch) => es.Search(q, _p) in User)
        case (Group.`basicName`, _p) =>
          Some((es: ElasticSearch) => es.Search(q, _p) in Group)
        case _                       => None
      }

      if (defs.isEmpty)
        Future.successful(Ok(Json.arr()))
      else
        ES.Multi(defs: _*).future()
          .map(PageMSResp(p, _)).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.SearchCtrl.index(types, q, _))
          )
        }
    }
}

object SearchCtrl extends Secured("search")