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
class Files(
  val basicPlayApi: BasicPlayApi
)(
  implicit
  val accessControlRepo: AccessControlRepo,
  val userRepo: UserRepo,
  internalGroupsRepo: InternalGroupsRepo,
  Home: Home,
  Directory: DirectoryRepo,
  File: FileRepo
)
  extends Secured(Files)
  with Controller
  with BasicPlayComponents
  with I18nSupport {

  def index(path: Path, pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.files.index(path, pager))
    }

  def show(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        home <- Home(req.user)
        file <- home.file(path)
      } yield file).map { file =>
        Ok(html.files.show(path, file))
      }.recover {
        case e: BaseException => NotFound(e.reason)
      }
    }
}

object Files extends Secured(CFS) with ViewMessages