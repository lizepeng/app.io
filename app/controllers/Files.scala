package controllers

import javax.inject.Inject

import controllers.api.SecuredController
import helpers._
import models.cfs._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import views._

import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
class Files @Inject()(val messagesApi: MessagesApi)
  extends SecuredController(CFS)
  with ViewMessages with I18nSupport {

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

object Files
  extends SecuredController(CFS)
  with ViewMessages