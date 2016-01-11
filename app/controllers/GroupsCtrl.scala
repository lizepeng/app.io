package controllers

import java.util.UUID

import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import security._
import views._

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired
)
  extends Secured(GroupsCtrl)
  with Controller
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with I18nSupport {

  def index(pager: Pager, sort: Seq[SortField]) =
    UserAction(_.Index, _.Create, _.Save, _.Destroy).apply { implicit req =>
      val default = _groups.sorting(_.name.asc)
      Ok(html.groups.index(pager, if (sort.nonEmpty) sort else default))
    }

  def show(id: UUID) =
    UserAction(_.Show, _.AddRelation, _.DelRelation).async { implicit req =>
      _groups.find(id).map { grp =>
        if (grp.is_internal) MethodNotAllowed
        else Ok(html.groups.show(grp))
      }.recover {
        case e: BaseException => NotFound
      }
    }
}

object GroupsCtrl
  extends Secured(Group)
  with CanonicalNameBasedMessages
  with ViewMessages {

  def groupNames(groups: Seq[Group]) = Json.prettyPrint(
    JsObject(
      groups.map(g => g.id.toString -> JsString(g.name.self))
    )
  )

  def intGroupNames(groups: Seq[Group]) = Json.prettyPrint(
    JsObject(
      groups.zipWithIndex.map(p => p._2.toString -> JsString(p._1.name.self))
    )
  )
}