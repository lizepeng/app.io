package controllers

import controllers.Users.{Password, Rules}
import helpers._
import models._
import models.sys.SysConfig
import play.api.Play.current
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
class PasswordReset(
  val basicPlayApi: BasicPlayApi
)(
  implicit val mailService: MailService
)
  extends Controller
  with ModuleLike
  with ViewMessages
  with SysConfig
  with AppConfig
  with BasicPlayComponents
  with I18nSupport {

  override val moduleName = "password_reset"

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

  def nnew(email: String) = MaybeUserAction { implicit req =>
    Ok(html.password_reset.nnew(emailFM.fill(email)))
  }

  /**
   * TODO count access, if someone try to enumerate our users, then ban
   *
   * @return
   */
  def create = MaybeUserAction.async { implicit req =>
    emailFM.bindFromRequest().fold(
      failure => Future.successful {
        onError(emailFM, "email.not.found")
      },
      success => (for {
        user <- User.find(success)
        link <- ExpirableLink.nnew(fullModuleName)(user)
        tmpl <- getEmailTemplate(s"$moduleName.email1")
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
  def show(id: String) = MaybeUserAction.async { implicit req =>
    ExpirableLink.find(id).map { ln =>
      if (ln.module != fullModuleName)
        onError(emailFM, "invalid.reset.link")
      else
        Ok(html.password_reset.show(id)(resetFM))
    }.recover {
      case e: ExpirableLink.NotFound =>
        onError(emailFM, "invalid.reset.link")
    }
  }

  def save(id: String) = MaybeUserAction.async { implicit req =>
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
        user <- User.find(link.user_id).flatMap(_.savePassword(success.original))
        tmpl <- getEmailTemplate(s"$moduleName.email2")
      } yield (user, tmpl)).map { case (user, tmpl) =>
        mailService.schedule("support", tmpl, user)
        Redirect(routes.Sessions.nnew()).flashing(
          AlertLevel.Info -> msg("password.changed")
        )
      }.recover {
        case e: ExpirableLink.NotFound =>
          onError(emailFM, "invalid.reset.link")
      }
    )
  }

  private lazy val mailer = for {
    uid <- System.UUID("user.id")
    usr <- User.find(uid).recoverWith {
      case e: User.NotFound => User.save(
        User(
          id = uid,
          name = moduleName,
          email = s"$moduleName@$domain"
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
        .recoverWith { case e: EmailTemplate.NotFound =>
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

object PasswordReset
  extends ModuleLike
  with ViewMessages {

  override val moduleName = "password_reset"
}