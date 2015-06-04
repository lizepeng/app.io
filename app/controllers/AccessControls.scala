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
class AccessControls(
  val basicPlayApi: BasicPlayApi
)(
  implicit val secured: RegisteredSecured
)
  extends Secured(AccessControls)
  with Controller
  with BasicPlayComponents
  with I18nSupport {

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.access_controls.index(pager))
    }

}

object AccessControls
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