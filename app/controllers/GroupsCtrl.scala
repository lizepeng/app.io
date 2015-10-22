package controllers

import java.util.UUID

import helpers._
import models._
import play.api.i18n._
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

  def index(pager: Pager, sort: Seq[String]) =
    UserAction(_.Index, _.Create, _.Save, _.Destroy).apply { implicit req =>
      val default = Seq(s" ${_groups.name.name}")
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
  with ViewMessages