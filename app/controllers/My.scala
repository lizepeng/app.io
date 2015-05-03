package controllers

import controllers.Users.{Password, Rules}
import controllers.api.SecuredController
import elasticsearch.ES
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

  val ProfileFM = Form(single("name" -> nonEmptyText(minLength = 2)))

  def dashboard =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.dashboard())
    }

  def account =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.account(ChangePasswordFM))
    }

  def changePassword =
    (MaybeUserAction >> AuthCheck).async { implicit req =>

      val bound = ChangePasswordFM.bindFromRequest()
      bound.fold(
        failure =>
          Future.successful {
            BadRequest(html.my.account(bound))
          },
        success => {
          if (!req.user.hasPassword(success.old_password))
            Future.successful {
              BadRequest(
                html.my.account(
                  bound.withGlobalError(msg("old.password.invalid"))
                )
              )
            }
          else
            req.user.savePassword(
              success.new_password.original
            ).map { user =>
              Redirect(routes.My.account()).flashing {
                AlertLevel.Info -> msg("password.changed")
              }.createSession(rememberMe = false)(user)
            }
        }
      )
    }

  def profile =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.profile(ProfileFM.fill(req.user.name)))
    }

  def changeProfile =
    (MaybeUserAction >> AuthCheck).async { implicit req =>
      val bound = ProfileFM.bindFromRequest()
      bound.fold(
        failure =>
          Future.successful {
            BadRequest(html.my.profile(failure))
          },
        success =>
          for {
            _ <- User.saveName(req.user.id, success)
            _ <- ES.Index(req.user.copy(name = success)) into User
          } yield {
            Ok(html.my.profile(ProfileFM.fill(success)))
          }
      )
    }
}