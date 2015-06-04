package controllers

import controllers.Users.{Password, Rules}
import controllers.api.Secured
import elasticsearch.ElasticSearch
import helpers._
import models.{Person, User}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class My(
  val basicPlayApi: BasicPlayApi,
  val ES: ElasticSearch
)
  extends Secured(User)
  with Controller
  with Session
  with BasicPlayComponents
  with CanonicalNameBasedMessages
  with I18nSupport {

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

  val ProfileFM = Form(
    tuple(
      "first_name" -> nonEmptyText(minLength = 1),
      "last_name" -> nonEmptyText(minLength = 1)
    )
  )

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
    (MaybeUserAction >> AuthCheck).async { implicit req =>
      Person.find(req.user.id).map { p =>
        Ok(html.my.profile(filledWith(p)))
      }.recover {
        case e: Person.NotFound =>
          Ok(html.my.profile(ProfileFM))
      }
    }

  def changeProfile =
    (MaybeUserAction >> AuthCheck).async { implicit req =>
      val bound = ProfileFM.bindFromRequest()

      bound.fold(
        failure =>
          Future.successful {
            BadRequest(html.my.profile(failure))
          },
        _ match { case (first, last) =>
          for {
            p <- Future.successful(Person(req.user.id, first, last))
            _ <- Person.save(p)
            _ <- ES.Index(p) into Person
          } yield {
            Ok(html.my.profile(filledWith(p)))
          }
        }
      )
    }

  def filledWith(p: Person) =
    ProfileFM.fill(
      p.first_name,
      p.last_name
    )
}