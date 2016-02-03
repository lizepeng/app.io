package controllers

import elasticsearch._
import helpers._
import models._
import models.misc._
import play.api.i18n._
import play.api.mvc.Controller
import security.ModulesAccessControl._
import security._
import views._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val secured: RegisteredSecured
)
  extends Secured(AccessControlsCtrl)
  with Controller
  with BasicPlayComponents
  with UserActionComponents
  with I18nSupport {

  def index(pager: Pager, sort: Seq[SortField]) =
    UserAction(_.Index, _.Save, _.Create, _.Destroy).apply { implicit req =>
      val default = _accessControls.sorting(
        _.principal_id.desc,
        _.resource.asc
      )
      Ok(html.access_controls.index(pager, if (sort.nonEmpty) sort else default))
    }

}

object AccessControlsCtrl
  extends Secured(AccessControl)
  with CanonicalNameBasedMessages
  with ViewMessages
  with Logging {

  def initIfFirstRun(
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
          AccessControlEntry(
            resource,
            AccessDefinition.Anything.self,
            _internalGroups.AnyoneId,
            is_group = true
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