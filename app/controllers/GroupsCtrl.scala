package controllers

import java.util.UUID

import controllers.api.Secured
import helpers._
import models._
import models.sys.{SysConfig, SysConfigs}
import play.api.data.Forms._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Controller
import protocols.JsonProtocol._
import views._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  val basicPlayApi: BasicPlayApi
)(
  implicit
  val accessControlRepo: AccessControls,
  val groupRepo: Groups,
  val userRepo: Users
)
  extends Secured(GroupsCtrl)
  with Controller
  with BasicPlayComponents
  with I18nSupport {

  val mapping_name = "name" -> nonEmptyText(2, 255)

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.groups.index(pager))
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      groupRepo.find(id).map { grp =>
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
  with ViewMessages
  with SysConfig
  with Logging {

  import html.layouts._

  val layout_admin  = base_admin.getClass.getCanonicalName
  val layout_normal = base_normal.getClass.getCanonicalName

  @volatile private var _gid2layouts: Map[UUID, String] = Map()

  def initialize(
    implicit groupRepo: Groups,
  sysConfigRepo: SysConfigs): Future[Map[UUID, String]] = {
    groupRepo.all |>>> Iteratee.foldM(Map[UUID, String]()) { (map, grp) =>
      for (
        conf <-
        if (grp.id == groupRepo._internalGroups.AnyoneId)
          System.config(grp.id.toString, layout_admin).map(grp.id -> _)
        else
          System.config(grp.id.toString, layout_normal).map(grp.id -> _)
      ) yield map + conf
    }
  }.andThen {
    case Success(map) =>
      _gid2layouts = map
      Logger.info("Map(Group -> Layout) has been initialized.")
  }

  //TODO rename or extract to class
  def layouts(ids: Traversable[UUID]): Set[String] = {
    ids.flatMap(_gid2layouts.get).toSet
  }
}