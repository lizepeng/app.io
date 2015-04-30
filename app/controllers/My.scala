package controllers

import controllers.Users.{Password, Rules}
import controllers.api.SecuredController
import models.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object My extends SecuredController(User) with Session {

  val ChangePasswordFM = Form[ChangePasswordFD](
    mapping(
      "old_password" -> text.verifying(Rules.password),
      "new_password" -> mapping(
        "original" -> text.verifying(Rules.password),
        "confirmation" -> text
      )(Password.apply)(Password.unapply)
        .verifying("password.not.confirmed", _.isConfirmed)
    )(ChangePasswordFD.apply)(ChangePasswordFD.unapply)
  )

  case class ChangePasswordFD(
    old_password: String,
    new_password: Password
  )

  def dashboard =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.dashboard())
    }

  def admin =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.admin(ChangePasswordFM))
    }

  def changePassword =
    (MaybeUserAction >> AuthCheck).async { implicit req =>

      val bound = ChangePasswordFM.bindFromRequest()
      bound.fold(
        failure =>
          Future.successful {
            BadRequest(html.my.admin(bound))
          },
        success => {
          if (!req.user.hasPassword(success.old_password))
            Future.successful {
              BadRequest(
                html.my.admin(
                  bound.withGlobalError(msg("old.password.invalid"))
                )
              )
            }
          else
            req.user.savePassword(
              success.new_password.original
            ).map { user =>
              Redirect(routes.My.admin()).flashing {
                AlertLevel.Info -> msg("password.changed")
              }.createSession(rememberMe = false)(user)
            }
        }
      )
    }
}