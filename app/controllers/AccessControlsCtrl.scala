package controllers

import controllers.api.Secured
import helpers._
import models._
import play.api.i18n._
import play.api.mvc.Controller
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val secured: RegisteredSecured,
  val _groups: Groups
)
  extends Secured(AccessControlsCtrl)
  with Controller
  with BasicPlayComponents
  with PermCheckComponents
  with I18nSupport {

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.access_controls.index(pager))
    }

}

object AccessControlsCtrl
  extends Secured(AccessControl)
  with ViewMessages {

  def initialize: Future[Boolean] = Future.successful(true)

  //    for {
  //    _empty <- AccessControl.isEmpty
  //    result <-
  //    if (_empty) Future.sequence(
  //      Secured.Modules.names.map { resource =>
  //        AccessControl(
  //          resource,
  //          CheckedActions.Anything.name,
  //          InternalGroups.AnyoneId,
  //          is_group = true,
  //          granted = true
  //        ).save.flatMap { saved =>
  //          ES.Index(saved) into AccessControl
  //        }
  //      }
  //    ).andThen {
  //      case Success(_) => Logger.info("Granted permission to anyone")
  //    }.map(_ => true)
  //    else Future.successful(false)
  //  } yield result
}