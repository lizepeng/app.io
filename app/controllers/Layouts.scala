package controllers

import java.util.UUID

import controllers.api_internal.Group2Layout
import helpers._
import models.Groups
import models.sys.SysConfigs
import play.api.i18n.Messages
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import views.html

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
object Layouts
  extends Group2Layout
  with Logging {

  import html.layouts._

  val layout_admin  = Group2Layout.Layout(base_admin.getClass.getCanonicalName)
  val layout_normal = Group2Layout.Layout(base_normal.getClass.getCanonicalName)

  def toJson(implicit messages: Messages) =
    Json.prettyPrint(
      Json.obj(
        layout_admin.layout -> messages("layout.admin"),
        layout_normal.layout -> messages("layout.normal"),
        "" -> messages("layout.nothing")
      )
    )

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
          Group2Layout.getOrElseUpdate(grp.id, layout_admin).map(grp.id -> _.layout)
        else
          Group2Layout.getOrElseUpdate(grp.id, layout_normal).map(grp.id -> _.layout)
      ) yield map + conf
    }
  }.andThen {
    case Success(map) =>
      _gid2layouts = map
      Logger.info("Map(Group -> Layout) has been initialized.")
      map.foreach { case (gid, layout) => Logger.debug(s"$gid -> $layout") }
  }

  def apply(ids: Traversable[UUID]): Set[String] = {
    ids.flatMap(_gid2layouts.get).toSet
  }
}