package controllers

import helpers._
import models._
import models.misc._
import play.api.i18n.Messages
import play.api.libs.json.Json
import views.html

import scala.concurrent._
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
object Layouts extends Logging {

  import html.layouts._

  val layout_admin  = Layout(base_admin.getClass.getCanonicalName)
  val layout_normal = Layout(base_normal.getClass.getCanonicalName)

  def toJson(implicit messages: Messages) =
    Json.prettyPrint(
      Json.obj(
        layout_admin.layout -> messages("layout.admin"),
        layout_normal.layout -> messages("layout.normal"),
        "" -> messages("layout.nothing")
      )
    )

  def initIfFirstRun(
    implicit
    _internalGroups: InternalGroups,
    ec: ExecutionContext
  ): Future[Boolean] = {
    _internalGroups
      .setLayout(_internalGroups.AnyoneId, layout_admin)
      .andThen { case Success(true) =>
        Logger.debug("Initialized layout of Anyone")
      }
  }
}