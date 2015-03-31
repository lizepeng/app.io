package controllers

import controllers.session._
import helpers._
import models.EmailTemplate
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import security.{FilePerms, PermCheck}
import views._

import scala.util.Failure

object EmailTemplates extends Controller with Logging with AppConfig {

  override val module_name: String = "controllers.email_templates"

  def index(pager: Pager) =
    (UserAction >> PermCheck(
      module_name, "list",
      onDenied = req => Forbidden
    )).async { implicit req =>
      EmailTemplate.list(pager).map { list =>
        Ok(html.email_templates.index(Page(pager, list)))
      }.andThen {
        case Failure(e: FilePerms.Denied) => Logger.trace(e.reason)
      }.recover {
        case e: FilePerms.Denied => Forbidden
        case e: BaseException    => NotFound
      }
    }
}