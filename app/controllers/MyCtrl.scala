package controllers

import controllers.UsersCtrl._
import elasticsearch.ElasticSearch
import helpers._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.Controller
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class MyCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups,
  val _persons: Persons,
  val es: ElasticSearch
)
  extends Secured(User)
  with Controller
  with BasicPlayComponents
  with UsersComponents
  with DefaultPlayExecutor
  with I18nSupport
  with Session
  with CanonicalNameBasedMessages {

  val ChangePasswordFM = Form[ChangePasswordFD](
    mapping(
      "old_password" -> Password.constrained,
      "new_password" -> mapping(
        "original" -> Password.constrained,
        "confirmation" -> text
      )(PasswordConfirmation.apply)(PasswordConfirmation.unapply)
        .verifying("password.not.confirmed", _.isConfirmed)
    )(ChangePasswordFD.apply)(ChangePasswordFD.unapply)
  )

  case class ChangePasswordFD(
    old_password: Password,
    new_password: PasswordConfirmation
  )

  val ProfileFM = Form(
    tuple(
      "first_name" -> HumanName.constrained,
      "last_name" -> HumanName.constrained
    )
  )

  def dashboard =
    (MaybeUserAction() andThen AuthChecker) { implicit req =>
      Ok(html.my.dashboard())
    }

  def account =
    (MaybeUserAction() andThen AuthChecker) { implicit req =>
      Ok(html.my.account(ChangePasswordFM))
    }

  def changePassword =
    (MaybeUserAction() andThen AuthChecker).async { implicit req =>

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
                  bound.withGlobalError(message("old.password.invalid"))
                )
              )
            }
          else
            req.user.updatePassword(
              success.new_password.original
            ).map { user =>
              Redirect(routes.MyCtrl.account()).flashing {
                AlertLevel.Info -> message("password.changed")
              }.createSession(rememberMe = false)(user)
            }
        }
      )
    }

  def profile =
    (MaybeUserAction() andThen AuthChecker).async { implicit req =>
      _persons.find(req.user.id).map { p =>
        Ok(html.my.profile(filledWith(p)))
      }.recover {
        case e: Person.NotFound =>
          Ok(html.my.profile(ProfileFM))
      }
    }

  def changeProfile =
    (MaybeUserAction() andThen AuthChecker).async { implicit req =>
      val bound = ProfileFM.bindFromRequest()

      bound.fold(
        failure =>
          Future.successful {
            BadRequest(html.my.profile(failure))
          },
        _ match { case (first, last) =>
          for {
            p <- Future.successful(Person(req.user.id, first, last))
            _ <- _persons.save(p)
            _ <- es.Index(p) into _persons
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