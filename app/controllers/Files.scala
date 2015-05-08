package controllers

import controllers.api.SecuredController
import helpers._
import models._
import models.cfs._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
object Files
  extends SecuredController(CFS)
  with ViewMessages {

  def index(path: Path, pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      (for {
        home <- Home(req.user)
        curr <- home.dir(path) if FilePermission(curr).rx.?
        list <- curr.list(pager)
      } yield list).map { list =>
        Ok(html.files.index(path, Page(pager, list)))
      }.andThen {
        case Failure(e: FilePermission.Denied) => Logger.trace(e.reason)
      }.recover {
        case e: FilePermission.Denied => Forbidden
        case e: BaseException         => NotFound
      }
    }

  def show(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      serveFile(path) { file => Ok(html.files.show(path, file)) }
    }

  private def serveFile(path: Path)(block: File => Result)(
    implicit req: UserRequest[_]
  ): Future[Result] = {
    (for {
      home <- Home(req.user)
      file <- home.file(path)
    } yield file).map {
      block(_)
    }.recover {
      case e: BaseException => NotFound(e.reason)
    }
  }

}