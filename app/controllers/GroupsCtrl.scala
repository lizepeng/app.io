package controllers

import java.util.UUID

import helpers._
import models._
import play.api.data.Forms._
import play.api.i18n._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import security._
import views._

import scala.concurrent.Future

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

  val mapping_name = "name" -> nonEmptyText(2, 255)

  def index(pager: Pager) =
    UserAction(_.Index).apply { implicit req =>
      Ok(html.groups.index(pager))
    }

  def show(id: UUID) =
    UserAction(_.Show).async { implicit req =>
      _groups.find(id).map { grp =>
        Ok(html.groups.show(grp))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def checkName =
    UserAction(_.Show).async(parse.json) { implicit req =>
      Future.successful {
        req.body.validate(Group.nameReads).fold(
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