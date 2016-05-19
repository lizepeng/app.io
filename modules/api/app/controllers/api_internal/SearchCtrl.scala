package controllers.api_internal

import controllers.RateLimitConfigComponents
import elasticsearch._
import helpers._
import models._
import models.misc._
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
) extends SearchCtrlCNamed
  with CheckedModuleName
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with AuthenticateBySessionComponents
  with UserActionRequiredComponents
  with UserActionComponents[SearchCtrl.AccessDef]
  with SearchCtrl.AccessDef
  with ExceptionHandlers
  with RateLimitConfigComponents
  with DefaultPlayExecutor
  with I18nSupport {

  def index(types: Seq[String], q: Option[String], p: Pager, sort: Seq[SortField]) =
    UserAction(_.P16).async { implicit req =>
      val indexTypes = types.distinct

      val defs = indexTypes.zip(p / indexTypes.size).flatMap {
        case (name, _p) if name == _users.basicName  =>
          Some((es: ElasticSearch) => es.Search(q, _p, sort, Some(false)) in _users)
        case (name, _p) if name == _groups.basicName =>
          Some((es: ElasticSearch) => es.Search(q, _p, sort, Some(false)) in _groups)
        case _                                       => None
      }

      if (defs.isEmpty)
        Future.successful(Ok(Json.arr()))
      else
        es.Multi(defs: _*).future()
          .map(PageMSResp(p, _)).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.SearchCtrl.index(types, q, _, sort))
          )
        }
    }
}

object SearchCtrl
  extends SearchCtrlCNamed
    with PermissionCheckable {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    /** Search */
    val P16 = Access.Pos(16)

    def values = Seq(P16)
  }

  object AccessDef extends AccessDef
}

trait SearchCtrlCNamed extends CanonicalNamed {

  def basicName: String = "search"
}