package controllers

import java.util.UUID

import helpers._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Result
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object AccessControls extends MVController(AccessControl) {

  implicit val access_control_writes = Json.writes[AccessControl]

  val AccessControlFM = Form[AccessControl](
    mapping(
      "resource" -> nonEmptyText
        .verifying(
          "resource.not.exists",
          Secured.Modules.names.contains(_)
        ),
      "action" -> nonEmptyText
        .verifying(
          "action.not.exists",
          Secured.Actions.names.contains(_)
        ),
      "principal" -> of[UUID],
      "is_group" -> default(boolean, false),
      "granted" -> default(boolean, false)
    )(AccessControl.apply)(AccessControl.unapply)
  )

  def index(pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      index0(pager, AccessControlFM)
    }

  def save(
    principal: UUID,
    resource: String,
    action: String,
    is_group: Boolean
  ) = PermCheck(_.Save).async { implicit req =>
    val form = Form(single("value" -> boolean))
    form.bindFromRequest().fold(
      failure => Future.successful(Forbidden(failure.errorsAsJson)),
      success => AccessControl(
        resource, action, principal, is_group, success
      ).save.map { ac => Ok(Json.obj("value" -> ac.granted)) }
    )
  }

  def destroy(
    principal: UUID,
    resource: String,
    action: String,
    is_group: Boolean
  ) = PermCheck(_.Destroy).async { implicit req =>
    AccessControl.remove(principal, resource, action, is_group).map { _ =>
      RedirectToPreviousURI
        .getOrElse(
          Redirect(routes.AccessControls.index())
        ).flashing(
          AlertLevel.Success -> msg("entry.deleted")
        )
    }
  }

  def create(pager: Pager) =
    PermCheck(_.Create).async { implicit req =>
      val bound = AccessControlFM.bindFromRequest()
      bound.fold(
        failure => index0(pager, bound),
        success => success.save.flatMap { _ =>
          index0(
            pager, AccessControlFM,
            AlertLevel.Success -> msg("entry.created")
          )
        }
      )
    }

  private def index0(
    pager: Pager,
    fm: Form[AccessControl],
    flash: (String, String)*
  )(implicit req: UserRequest[_]): Future[Result] = {
    (for {
      list <- AccessControl.list(pager)
      usrs <- User.find(list.filter(!_.is_group).map(_.principal))
      grps <- Group.find(list.filter(_.is_group).map(_.principal))
    } yield (list, usrs, grps)).map { case (l, u, g) =>
      Ok(
        html.access_controls.index(Page(pager, l), u, g, fm)
      ).flashing(flash: _*)
    }.recover {
      case e: BaseException => NotFound
    }
  }
}