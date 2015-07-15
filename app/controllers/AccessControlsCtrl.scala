package controllers

import controllers.api.Secured
import elasticsearch.ElasticSearch
import helpers._
import models._
import play.api.i18n._
import play.api.mvc.Controller
import security.CheckedActions
import views._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val permCheckRequired: PermCheckRequired,
  val secured: RegisteredSecured
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
  with CanonicalNameBasedMessages
  with ViewMessages
  with Logging {

  def initIfEmpty(
    implicit
    secured: RegisteredSecured,
    es: ElasticSearch,
    ec: ExecutionContext,
    _internalGroups: InternalGroups,
    _accessControls: AccessControls
  ): Future[Boolean] =
    for {
      _empty <- _accessControls.isEmpty
      result <-
      if (_empty) Future.sequence(
        secured.Modules.names.map { resource =>
          AccessControl(
            resource,
            CheckedActions.Anything.name,
            _internalGroups.AnyoneId,
            is_group = true,
            granted = true
          ).save.flatMap { saved =>
            es.Index(saved) into _accessControls
          }
        }
      ).andThen {
        case Success(_) => Logger.info("Granted permission to anyone")
      }.map(_ => true)
      else Future.successful(false)
    } yield result
}