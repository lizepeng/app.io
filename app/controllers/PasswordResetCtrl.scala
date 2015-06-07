package controllers

import controllers.UsersCtrl.{Password, Rules}
import controllers.api.Secured
import helpers._
import models._
import models.sys.{SysConfigs, SysConfig}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Controller, Result}
import security._
import services._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class PasswordResetCtrl(
  val basicPlayApi: BasicPlayApi
)(
  implicit
  val mailService: MailService,
  val _users: Users,
  val ExpirableLink: ExpirableLinks,
  val EmailTemplate: EmailTemplates,
  val sysConfigRepo: SysConfigs
)
  extends Secured(PasswordResetCtrl)
  with Controller
  with CanonicalNameBasedMessages
  with SysConfig
  with AppConfig
  with BasicPlayComponents
  with InternalGroupsComponents
  with I18nSupport
  with Logging {

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

  def nnew(email: String) =
    MaybeUserAction().apply { implicit req =>
    Ok(html.password_reset.nnew(emailFM.fill(email)))
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
        link <- ExpirableLink.nnew(canonicalName)(user)
        tmpl <- getEmailTemplate(s"$basicName.email1")
      } yield (user, link.id, tmpl)).map { case (u, id, tmpl) =>
        mailService.schedule("noreply", tmpl, u, "link" -> id)
        Ok(html.password_reset.sent())
      }.recover {
        case e: models.User.NotFound          =>
          onError(emailFM, "email.not.found")
        case e: models.EmailTemplate.NotFound =>
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
    ExpirableLink.find(id).map { ln =>
      if (ln.module != canonicalName)
        onError(emailFM, "invalid.reset.link")
      else
        Ok(html.password_reset.show(id)(resetFM))
    }.recover {
      case e: models.ExpirableLink.NotFound =>
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
        link <- ExpirableLink.find(id).andThen { case _ => ExpirableLink.remove(id) }
        user <- _users.find(link.user_id).flatMap(_.savePassword(success.original))
        tmpl <- getEmailTemplate(s"$basicName.email2")
      } yield (user, tmpl)).map { case (user, tmpl) =>
        mailService.schedule("support", tmpl, user)
        Redirect(routes.SessionsCtrl.nnew()).flashing(
          AlertLevel.Info -> msg("password.changed")
        )
      }.recover {
        case e: models.ExpirableLink.NotFound =>
          onError(emailFM, "invalid.reset.link")
      }
    )
  }

  private lazy val mailer = for {
    uid <- System.UUID("user.id")
    usr <- _users.find(uid).recoverWith {
      case e: models.User.NotFound => _users.save(
        models.User(
          id = uid,
          name = basicName,
          email = s"$basicName@$domain"
        )
      )
    }
  } yield usr

  private def getEmailTemplate(key: String)(
    implicit lang: Lang
  ): Future[EmailTemplate] =
    for {
      uuid <- System.UUID(key)
      user <- mailer
      tmpl <- EmailTemplate.find(uuid, lang)
        .recoverWith {
        case e: models.EmailTemplate.NotFound =>
        EmailTemplate.save(
          EmailTemplate(
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

  private def onError(bound: Form[String], key: String)(
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