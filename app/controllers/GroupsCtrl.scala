package controllers

import java.util.UUID

import controllers.api.Secured
import helpers._
import models._
import play.api.data.Forms._
import play.api.i18n._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import views._

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  implicit
  val _basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val _groups: Groups
)
  extends Secured(GroupsCtrl)
  with Controller
  with BasicPlayComponents
  with PermCheckComponents
  with DefaultPlayExecutor
  with I18nSupport {

  val mapping_name = "name" -> nonEmptyText(2, 255)

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.groups.index(pager))
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      _groups.find(id).map { grp =>
        Ok(html.groups.show(grp))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def checkName =
    PermCheck(_.Show).async(parse.json) { implicit req =>
      Future.successful {
        req.body.validate(Group.name.reads).fold(
          failure => UnprocessableEntity(JsonClientErrors(failure)),
          success => Ok
        )
      }
    }

}

object GroupsCtrl
  extends Secured(Group)
  with CanonicalNameBasedMessages
  with ViewMessages