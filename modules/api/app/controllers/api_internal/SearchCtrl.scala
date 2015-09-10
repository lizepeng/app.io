package controllers.api_internal

import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.json.Json
import play.api.mvc.Controller
import protocols._
import security._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class SearchCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _groups: Groups
)
  extends Secured(SearchCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with I18nSupport {

  def index(types: Seq[String], q: Option[String], p: Pager) =
    UserAction(_.Index, rateLimit = 2000).async { implicit req =>
      val indexTypes = types.distinct

      val defs = indexTypes.zip(p / indexTypes.size).flatMap {
        case (name, _p) if name == _users.basicName  =>
          Some((es: ElasticSearch) => es.Search(q, _p) in _users)
        case (name, _p) if name == _groups.basicName =>
          Some((es: ElasticSearch) => es.Search(q, _p) in _groups)
        case _                                       => None
      }

      if (defs.isEmpty)
        Future.successful(Ok(Json.arr()))
      else
        es.Multi(defs: _*).future()
          .map(PageMSResp(p, _)).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.SearchCtrl.index(types, q, _))
          )
        }
    }
}

object SearchCtrl extends Secured("search")