package controllers

import elasticsearch._
import elasticsearch.mappings.AccessControlMapping
import helpers._
import models._
import models.misc._
import play.api.i18n._
import play.api.mvc.Controller
import security._
import views._

import scala.concurrent._
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val secured: RegisteredSecured
) extends AccessControlCanonicalNamed
  with CheckedModuleName
  with Controller
  with BasicPlayComponents
  with DefaultPlayExecutor
  with AuthenticateBySessionComponents
  with UserActionRequiredComponents
  with UserActionComponents[AccessControlsCtrl.AccessDef]
  with AccessControlsCtrl.AccessDef
  with ExceptionHandlers
  with I18nSupport {

  def index(q: Option[String], pager: Pager, sort: Seq[SortField]) =
    UserAction(_.P01, _.P03, _.P05, _.P06).apply { implicit req =>
      val default = _accessControls.sorting(
        _.principal_id.desc,
        _.resource.asc
      )
      Ok(html.access_controls.index(q, pager, if (sort.nonEmpty) sort else default))
    }

}

object AccessControlsCtrl
  extends AccessControlCanonicalNamed
    with PermissionCheckable
    with CanonicalNameBasedMessages
    with ViewMessages
    with BootingProcess
    with Logging {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    def values = Seq(P01, P03, P05, P06)
  }

  object AccessDef extends AccessDef

  def initIfFirstRun(
    implicit
    secured: RegisteredSecured,
    es: ElasticSearch,
    ec: ExecutionContext,
    _internalGroups: InternalGroups,
    _accessControls: AccessControls
  ): Future[Boolean] = {

    onStart(es.PutMapping(AccessControlMapping))

    for {
      _empty <- _accessControls.isEmpty
      result <-
      if (_empty) Future.sequence(
        secured.modules.map { resource =>
          AccessControlEntry(
            resource.checkedModuleName.name,
            resource.AccessDef.permission.self,
            _internalGroups.AnyoneId,
            is_group = true
          ).save.flatMap { saved =>
            es.Index(saved) into _accessControls
          }
        }
      ).andThen {
        case Success(_) => Logger.info("Granted permission to anyone.")
      }.map(_ => true)
      else Future.successful(false)
    } yield result
  }
}