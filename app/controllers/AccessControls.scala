package controllers

import javax.inject.Inject

import controllers.api.SecuredController
import elasticsearch.ES
import helpers._
import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import security.CheckedActions
import views._

import scala.concurrent.Future
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class AccessControls @Inject()(val messagesApi: MessagesApi)
  extends SecuredController(AccessControl)
  with I18nSupport {

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.access_controls.index(pager))
    }

}

object AccessControls
  extends SecuredController(AccessControl)
  with ViewMessages {

  def initialize: Future[Boolean] = for {
    _empty <- AccessControl.isEmpty
    result <-
    if (_empty) Future.sequence(
      Secured.Modules.names.map { resource =>
        AccessControl(
          resource,
          CheckedActions.Anything.name,
          InternalGroups.AnyoneId,
          is_group = true,
          granted = true
        ).save.flatMap { saved =>
          ES.Index(saved) into AccessControl
        }
      }
    ).andThen {
      case Success(_) => Logger.info("Granted permission to anyone")
    }.map(_ => true)
    else Future.successful(false)
  } yield result
}