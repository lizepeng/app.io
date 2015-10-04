package controllers

import controllers.UsersCtrl._
import helpers._
import models._
import models.sys.{SysConfig, SysConfigs}
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
)
  extends Secured(PasswordResetCtrl)
  with Controller
  with BasicPlayComponents
  with UsersComponents
  with InternalGroupsComponents
  with DefaultPlayExecutor
  with I18nSupport
  with CanonicalNameBasedMessages
  with SysConfig
  with AppConfigComponents
  with Logging {

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
          link <- _expirableLinks.nnew(canonicalName)(user)
          tmpl <- getEmailTemplate(s"$basicName.email1")
        } yield (user, link.id, tmpl)).map { case (u, id, tmpl) =>
          mailService.schedule("noreply", tmpl, u, "link" -> id)
          Ok(html.password_reset.sent())
        }.recover {
          case e: User.NotFound          =>
            onError(emailFM, "email.not.found")
          case e: EmailTemplate.NotFound =>
            Logger.warn(e.reason)
            onError(emailFM, "email.not.found")
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
  def show(id: String) =
    MaybeUserAction().async { implicit req =>
      _expirableLinks.find(id).map { ln =>
        if (ln.module != canonicalName)
          onError(emailFM, "invalid.reset.link")
        else
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
          link <- _expirableLinks.find(id).andThen { case _ => _expirableLinks.remove(id) }
          user <- _users.find(link.user_id).flatMap(_.updatePassword(success.original))
          tmpl <- getEmailTemplate(s"$basicName.email2")
        } yield (user, tmpl)).map { case (user, tmpl) =>
          mailService.schedule("support", tmpl, user)
          Redirect(routes.SessionsCtrl.nnew()).flashing(
            AlertLevel.Info -> message("password.changed")
          )
        }.recover {
          case e: ExpirableLink.NotFound =>
            onError(emailFM, "invalid.reset.link")
        }
      )
    }

  private lazy val mailer = for {
    uid <- System.UUID("user.id")
    usr <- _users.find(uid).recoverWith {
      case e: User.NotFound => _users.save(
        User(
          id = uid,
          name = Name(basicName),
          email = EmailAddress(s"$basicName@$domain")
        )
      )
    }
  } yield usr

  private def getEmailTemplate(key: String)(
    implicit messages: Messages
  ): Future[EmailTemplate] =
    for {
      uuid <- System.UUID(key)
      user <- mailer
      tmpl <- _emailTemplates.find(uuid, messages.lang)
        .recoverWith {
        case e: EmailTemplate.NotFound =>
          _emailTemplates.save(
            _emailTemplates.build(
              uuid, Lang.defaultLang,
              key,
              subject = "",
              text = "",
              user.id,
              user.id
            )
          )
      }
    } yield tmpl

  private def onError(bound: Form[EmailAddress], key: String)(
    implicit req: MaybeUserRequest[_]
  ): Result = NotFound {
    html.password_reset.nnew {
      bound.withGlobalError(msg_key(key))
    }
  }
}

object PasswordResetCtrl extends CanonicalNamed with ViewMessages {

  override val basicName = "password_reset"
}