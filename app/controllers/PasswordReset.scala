package controllers

import controllers.Users.{Password, Rules}
import controllers.session.UserAction
import helpers.Logging
import models.{ExpirableLink, User}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer.Email
import play.api.mvc.{Controller, Result}
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object PasswordReset extends Controller with Logging {

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
        u <- User.find(success)
        l <- ExpirableLink.nnew(module_name)(u)
      } yield (u, l.id)).map { case (u, id) =>
        Mailer.schedule(email1(u, id))
        Ok(html.password_reset.sent())
      }.recover {
        case e: User.NotFound =>
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
        l <- ExpirableLink.find(id)
        u <- User.find(l.user_id)
        s <- User.savePassword(u, success.original)
        r <- ExpirableLink.remove(id)
      } yield s).map { u =>
        Mailer.schedule(email2(u))
        Redirect(routes.Sessions.nnew()).flashing(
          AlertLevel.Info -> MSG(s"$module_name.password.changed")
        )
      }.recover {
        case e: ExpirableLink.NotFound =>
          onError(emailFM, s"$module_name.invalid.reset.link")
      }
    )
  }

  def email1(to: User, link: String) = Email(
    subject = s"[${MSG("app.name")}] Please reset your password",
    from = "",
    to = Seq(s"${to.name} <${to.email}>"),
    bodyText = Some(
      s"""
We heard that you lost your ${MSG("app.name")} password. Sorry about that!

But don't worry!You can use the following link within the next day to reset your password:
${MSG("app.url")}/password_reset/$link

If you don't use this link within 24 hours, it will expire. To get a new password reset link, visit
${MSG("app.url")}/password_reset

Thanks,
Your friends at ${MSG("app.name")}
      """
    )
  )

  def email2(to: User) = Email(
    subject = s"[${MSG("app.name")}] Your password has changed",
    from = "",
    to = Seq(s"${to.name} <${to.email}>"),
    bodyText = Some(
      s"""
Hello ${to.name},

We wanted to let you know that your ${MSG("app.name")} password was changed.

If you did not perform this action, you can recover access by entering ${to.email} into the form at ${MSG("app.url")}/password_reset.

To see this and other security events for your account, visit ${MSG("app.url")}/settings/security.

If you run into problems, please contact support by visiting ${MSG("app.url")}/contact or replying to this email.
      """
    )
  )

  private def onError(bound: Form[String], msg: String)(implicit req: UserRequest[_]): Result = NotFound {
    html.password_reset.nnew {
      bound.withGlobalError(msg)
    }
  }
}