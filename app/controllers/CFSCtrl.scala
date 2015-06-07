package controllers

import controllers.api.Secured
import helpers._
import models._
import models.cfs._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import views._

import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
class CFSCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val _groups: Groups,
  val cfs: CFS
)
  extends Secured(CFSCtrl)
  with Controller
  with BasicPlayComponents
  with PermCheckComponents
  with I18nSupport {

  def index(path: Path, pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.cfs.index(path, pager))
    }

  def show(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        home <- cfs.home(req.user)
        file <- home.file(path)
      } yield file).map { file =>
        Ok(html.cfs.show(path, file))
      }.recover {
        case e: BaseException => NotFound(e.reason)
      }
    }
}

object CFSCtrl extends Secured(CFS) with ViewMessages