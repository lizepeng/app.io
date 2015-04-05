package controllers

import controllers.Users.{Password, Rules}
import controllers.session.UserAction
import helpers.Logging
import models.sys.SysConfig
import models.{EmailTemplate, ExpirableLink, User}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Controller, Result}
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object PasswordReset extends Controller with Logging with SysConfig {

  override val module_name = "controllers.password_reset"

  val emailFM = Form(
    single("email" -> text.verifying(Rules.email))
  )

  val resetFM = Form[Password](
    mapping(
      "password.original" -> text.verifying(Rules.password),
      "password.confirmation" -> text
    )(Password.apply)(Password.unapply)
      .verifying("password.not.confirmed", _.isConfirmed)
  )

  def nnew = UserAction { implicit req =>
    Ok(html.password_reset.nnew(emailFM))
  }

  /**
   * TODO count access, if someone try to enumerate our users, then ban
   *
   * @return
   */
  def create = UserAction.async { implicit req =>
    emailFM.bindFromRequest().fold(
      failure => Future.successful {
        onError(emailFM, s"$module_name.email.not.found")
      },
      success => (for {
        user <- User.find(success)
        link <- ExpirableLink.nnew(module_name)(user)
        tmpl <- getUUID("email1.id").flatMap(id => EmailTemplate.find(id, req.lang))
      } yield (user, link.id, tmpl)).map { case (u, id, tmpl) =>
        Mailer.schedule(tmpl, u, "link" -> id)
        Ok(html.password_reset.sent())
      }.recover {
        case e: User.NotFound          =>
          onError(emailFM, s"$module_name.email.not.found")
        case e: EmailTemplate.NotFound =>
          Logger.warn(e.reason)
          onError(emailFM, s"$module_name.email.not.found")
      }

    )
  }

  /**
   * Show a form that user can enter new password, only if
   * the user got a valid password reset link in his mail inbox.
   *
   * @param id
   * @return
   */
  def show(id: String) = UserAction.async { implicit req =>
    ExpirableLink.find(id).map { ln =>
      if (ln.module != module_name)
        onError(emailFM, s"$module_name.invalid.reset.link")
      else
        Ok(html.password_reset.show(id)(resetFM))
    }.recover {
      case e: ExpirableLink.NotFound =>
        onError(emailFM, s"$module_name.invalid.reset.link")
    }
  }

  def save(id: String) = UserAction.async { implicit req =>
    val bound = resetFM.bindFromRequest()
    bound.fold(
      failure => Future.successful {
        BadRequest {
          html.password_reset.show(id) {
            if (bound.hasGlobalErrors) bound
            else bound.withGlobalError(s"$module_name.failed")
          }
        }
      },
      success => (for {
        link <- ExpirableLink.find(id).andThen { case _ => ExpirableLink.remove(id) }
        user <- User.find(link.user_id).flatMap(_.savePassword(success.original))
        tmpl <- getUUID("email2.id").flatMap(id => EmailTemplate.find(id, req.lang))
      } yield (user, tmpl)).map { case (user, tmpl) =>
        Mailer.schedule(tmpl, user)
        Redirect(routes.Sessions.nnew()).flashing(
          AlertLevel.Info -> MSG(s"$module_name.password.changed")
        )
      }.recover {
        case e: ExpirableLink.NotFound =>
          onError(emailFM, s"$module_name.invalid.reset.link")
      }
    )
  }

  private def onError(bound: Form[String], msg: String)(implicit req: UserRequest[_]): Result = NotFound {
    html.password_reset.nnew {
      bound.withGlobalError(msg)
    }
  }
}