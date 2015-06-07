package controllers.api

import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.json.Json
import play.api.mvc.Controller

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class SearchCtrl(
  implicit
  val _basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val _groups: Groups,
  val _es: ElasticSearch
)
  extends Secured(SearchCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with PermCheckComponents
  with DefaultPlayExecutor
  with I18nSupport {

  def index(types: Seq[String], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
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
        _es.Multi(defs: _*).future()
          .map(PageMSResp(p, _)).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.SearchCtrl.index(types, q, _))
          )
        }
    }
}

object SearchCtrl extends Secured("search")