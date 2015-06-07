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
  val _basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val _secured: RegisteredSecured
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
    accessControls: AccessControls,
    secured: RegisteredSecured,
    internalGroups: InternalGroups,
    elasticSearch: ElasticSearch,
    executor: ExecutionContext
  ): Future[Boolean] =
    for {
      _empty <- accessControls.isEmpty
      result <-
      if (_empty) Future.sequence(
        secured.Modules.names.map { resource =>
          AccessControl(
            resource,
            CheckedActions.Anything.name,
            internalGroups.AnyoneId,
            is_group = true,
            granted = true
          ).save.flatMap { saved =>
            elasticSearch.Index(saved) into accessControls
          }
        }
      ).andThen {
        case Success(_) => Logger.info("Granted permission to anyone")
      }.map(_ => true)
      else Future.successful(false)
    } yield result
}