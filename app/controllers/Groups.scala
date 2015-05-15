package controllers

import java.util.UUID

import controllers.api.{JsonClientErrors, SecuredController}
import helpers._
import models._
import models.sys.SysConfig
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import views._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
object Groups
  extends SecuredController(Group)
  with ViewMessages with SysConfig {

  val mapping_name = "name" -> nonEmptyText(2, 255)

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.groups.index(pager))
    }

  def show(id: UUID) =
    PermCheck(_.Show).async { implicit req =>
      Group.find(id).map { grp =>
        Ok(html.groups.show(grp))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def checkName =
    PermCheck(_.Show).async(parse.json) { implicit req =>
      Future.successful {
        req.body.validate(Group.reads_name).fold(
          failure => UnprocessableEntity(JsonClientErrors(failure)),
          success => Ok
        )
      }
    }

  import html.layouts._

  val layout_admin  = base_admin.getClass.getCanonicalName
  val layout_normal = base_normal.getClass.getCanonicalName

  @volatile private var _gid2layouts: Map[UUID, String] = Map()

  def initialize: Future[Map[UUID, String]] = {
    Group.all |>>> Iteratee.foldM(Map[UUID, String]()) { (map, grp) =>
      for (
        conf <-
        if (grp.id == InternalGroups.AnyoneId)
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

  def layouts(ids: Traversable[UUID]): Set[String] = {
    ids.flatMap(_gid2layouts.get).toSet
  }
}