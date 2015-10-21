package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.UsersCtrl.PasswordConfirmation
import elasticsearch.ElasticSearch
import helpers._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n._
import play.api.libs.json.Json
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class UsersCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch
)
  extends Secured(UsersCtrl)
  with Controller
  with security.Session
  with BasicPlayComponents
  with UserActionComponents
  with UsersComponents
  with InternalGroupsComponents
  with DefaultPlayExecutor
  with I18nSupport {

  val signUpFM = Form[SignUpFD](
    mapping(
      "email" -> EmailAddress.constrained,
      "password" -> mapping(
        "original" -> Password.constrained,
        "confirmation" -> text
      )(PasswordConfirmation.apply)(PasswordConfirmation.unapply)
        .verifying("password.not.confirmed", _.isConfirmed)
    )(SignUpFD.apply)(SignUpFD.unapply)
  )

  case class SignUpFD(
    email: EmailAddress,
    password: PasswordConfirmation
  )

  def show(id: UUID) =
    UserAction(_.Show).async { implicit req =>
      _users.find(id).map { user =>
        Ok(html.users.show(user))
      }
    }

  def index(pager: Pager) =
    UserAction(_.Index, _.Create).apply { implicit req =>
      Ok(html.users.index(pager))
    }

  def nnew = MaybeUserAction().apply { implicit req =>
    req.maybeUser match {
      case None    => Ok(views.html.users.signup(signUpFM))
      case Some(u) => Redirect(routes.MyCtrl.dashboard())
    }
  }

  def create = MaybeUserAction().async { implicit req =>
    val bound = signUpFM.bindFromRequest
    bound.fold(
      failure => Future.successful {
        BadRequest {
          html.users.signup {
            if (failure.hasGlobalErrors) failure
            else failure.withGlobalError("sign.up.failed")
          }
        }
      },
      success => (for {
        exist <- _users.checkEmail(success.email)
        saved <- User(
          id = UUIDs.timeBased,
          email = success.email,
          password = success.password.original
        ).save
        _____ <- es.Index(saved) into _users
      } yield saved).map { case saved =>
        Redirect {
          routes.MyCtrl.dashboard()
        }.createSession(rememberMe = false)(saved)
      }.recover {
        case e: User.EmailTaken =>
          BadRequest {
            html.users.signup {
              bound.withGlobalError("sign.up.failed")
                .withError("email", e.message)
            }
          }
      }
    )
  }

  def checkEmail = Action.async { implicit req =>
    val form = Form(single("value" -> EmailAddress.constrained))

    form.bindFromRequest().fold(
      failure => Future.successful(Forbidden(failure.errorsAsJson)),
      success => _users.checkEmail(success).map { _ =>
        Ok("")
      }.recover {
        case e: User.EmailTaken =>
          Forbidden(Json.obj("value" -> e.message))
      }
    )
  }

  def checkPassword = Action { implicit req =>
    val form = Form(single("value" -> Password.constrained))

    form.bindFromRequest().fold(
      failure => Forbidden(failure.errorsAsJson),
      success => Ok("")
    )
  }

}

object UsersCtrl
  extends Secured(User)
  with CanonicalNameBasedMessages
  with ViewMessages {

  case class PasswordConfirmation(original: Password, confirmation: String) {

    def isConfirmed = original.self == confirmation
  }
}