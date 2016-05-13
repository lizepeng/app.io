package controllers

import controllers.UsersCtrl._
import helpers._
import models._
import models.misc._
import models.sys._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n._
import play.api.mvc._
import security._
import services._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class PasswordResetCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val mailService: MailService,
  val _groups: Groups,
  val _expirableLinks: ExpirableLinks,
  val _emailTemplates: EmailTemplates,
  val _sysConfig: SysConfigs
) extends Controller
  with PasswordResetCtrlCNamed
  with CanonicalNameBasedMessages
  with BasicPlayComponents
  with DefaultPlayExecutor
  with UsersComponents
  with MaybeUserActionComponents
  with ExceptionHandlers
  with InternalGroupsComponents
  with AppDomainComponents
  with I18nLoggingComponents
  with SystemAccounts
  with I18nSupport {

  val emailFM = Form[EmailAddress](
    single("email" -> EmailAddress.constrained)
  )

  val resetFM = Form[PasswordConfirmation](
    mapping(
      "password.original" -> Password.constrained,
      "password.confirmation" -> text
    )(PasswordConfirmation.apply)(PasswordConfirmation.unapply)
      .verifying("password.not.confirmed", _.isConfirmed)
  )

  def nnew(email: Option[EmailAddress]) =
    MaybeUserAction().apply { implicit req =>
      Ok {
        val filled = emailFM.fill(email.getOrElse(EmailAddress.empty))
        html.password_reset.nnew(filled)
      }
    }

  /**
   * TODO count access, if someone try to enumerate our users, then ban
   *
   * @return
   */
  def create =
    MaybeUserAction().async { implicit req =>
      emailFM.bindFromRequest().fold(
        failure => Future.successful {
          onError(emailFM, "email.not.found")
        },
        success => (for {
          user <- _users.find(success)
          link <- _expirableLinks.save(user.id)
          tmpl <- getEmailTemplate(s"$basicName.email1")
        } yield (user, link.id, tmpl)).map { case (user, id, tmpl) =>
          mailService.sendTo(user)("noreply", tmpl, Map("link" -> id))
          Ok(html.password_reset.sent())
        }.recover {
          case e: User.NotFound          =>
            onError(emailFM, "email.not.found")
          case e: EmailTemplate.NotFound =>
            Logger.warn(e.reason, e)
            onError(emailFM, "email.not.found")
        }

      )
    }

  /**
   * Show a form that user can enter new password, only if
   * the user got a valid password reset link in his mail inbox.
   */
  def show(id: String) =
    MaybeUserAction().async { implicit req =>
      _expirableLinks.find(id).map { ln =>
        Ok(html.password_reset.show(id)(resetFM))
      }.recover {
        case e: ExpirableLink.NotFound =>
          onError(emailFM, "invalid.reset.link")
      }
    }

  def save(id: String) =
    MaybeUserAction().async { implicit req =>
      val bound = resetFM.bindFromRequest()
      bound.fold(
        failure => Future.successful {
          BadRequest {
            html.password_reset.show(id) {
              if (bound.hasGlobalErrors) bound
              else bound.withGlobalError(msg_key("failed"))
            }
          }
        },
        success => (for {
          link <- _expirableLinks.find(id)
          ____ <- _expirableLinks.remove(id)
          _uid <- Future(link.target_id)
          user <- _users.find(_uid)
          ____ <- user.updatePassword(success.original)
          tmpl <- getEmailTemplate(s"$basicName.email2")
        } yield (user, tmpl)).map { case (user, tmpl) =>
          mailService.sendTo(user)("support", tmpl)
          Redirect(routes.SessionsCtrl.nnew()).flashing(
            AlertLevel.Info -> message("password.changed")
          )
        }.recover {
          case e: ExpirableLink.NotFound => onError(emailFM, "invalid.reset.link")
          case e: Throwable              => onError(emailFM, "invalid.reset.link")
        }
      )
    }

  private def getEmailTemplate(key: String)(
    implicit messages: Messages
  ): Future[EmailTemplate] = _emailTemplates
    .getOrElseUpdate(key, messages.lang)(_systemAccount(PasswordResetCtrl))

  private def onError(bound: Form[EmailAddress], key: String)(
    implicit req: MaybeUserRequest[_]
  ): Result = NotFound {
    html.password_reset.nnew {
      bound.withGlobalError(msg_key(key))
    }
  }
}

object PasswordResetCtrl
  extends PasswordResetCtrlCNamed
    with ViewMessages

trait PasswordResetCtrlCNamed extends CanonicalNamed {

  val basicName = "password_reset"
}