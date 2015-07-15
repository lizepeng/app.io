package controllers

import java.util.UUID

import helpers.Logging
import models.Groups
import models.sys.{SysConfig, SysConfigs}
import play.api.libs.iteratee.Iteratee
import views.html

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */

object Layouts
  extends SysConfig
  with Logging {

  import html.layouts._

  val layout_admin  = base_admin.getClass.getCanonicalName
  val layout_normal = base_normal.getClass.getCanonicalName

  @volatile private var _gid2layouts: Map[UUID, String] = Map()

  def init(
    implicit
    groups: Groups,
    sysConfig: SysConfigs,
    ec: ExecutionContext
  ): Future[Map[UUID, String]] = {
    groups.all |>>> Iteratee.foldM(Map[UUID, String]()) { (map, grp) =>
      for (
        conf <-
        if (grp.id == groups._internalGroups.AnyoneId)
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

  def apply(ids: Traversable[UUID]): Set[String] = {
    ids.flatMap(_gid2layouts.get).toSet
  }
}