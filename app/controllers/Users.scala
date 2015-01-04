package controllers

import java.util.UUID

import controllers.Sessions._
import controllers.helpers.AuthCheck
import controllers.session._
import models.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Users extends Controller {

  val signUpFM = Form[SignUpFD](
    mapping(
      "email" -> text.verifying(Rules.email),
      "password" -> text.verifying(Rules.password),
      "password_confirmation" -> text
    )(SignUpFD.apply)(SignUpFD.unapply)
      .verifying("password.not.confirmed", _.isConfirmed)
  )

  case class SignUpFD(
    email: String,
    password: String,
    password_confirmation: String
  ) {
    def isConfirmed = password == password_confirmation
  }

  def show(id: UUID) =
    (UserAction andThen AuthCheck) {
      implicit request => Ok(html.users.show())
    }

  def index = TODO

  def nnew = UserAction {implicit request =>
    request.user match {
      case None    => Ok(views.html.users.signup(signUpFM))
      case Some(u) => Redirect(routes.Users.show(u.id))
    }
  }

  def create = UserAction.async {implicit request =>
    val fm = signUpFM.bindFromRequest
    fm.fold(
      fm => Future.successful {
        BadRequest {
          html.users.signup {
            if (fm.hasGlobalErrors) fm
            else fm.withGlobalError("signup.failed")
          }
        }
      },
      fd => User.findBy(fd.email).flatMap {
        case Some(_) => Future.successful {
          BadRequest {
            html.users.signup {
              fm.withGlobalError("signup.failed")
                .withError("email", "login.email.taken")
            }
          }
        }
        case None    => {
          User.save(User(email = fd.email, password = fd.password)).map {
            implicit u =>
              Redirect {
                routes.Users.show(u.id)
              }.createSession(rememberMe = false)
          }
        }
      }
    )
  }

  def checkEmail = Action.async {request =>
    val email = request.body.asFormUrlEncoded
      .flatMap(_.get("value"))
      .flatMap(_.headOption).getOrElse("")

    Rules.email.apply(email) match {
      case invalid: Invalid => Future.successful {
        Forbidden(invalid.errors.map(_.message).map(MSG(_)).mkString("\n"))
      }
      case Valid            => User.findBy(email).map {
        case None    => Ok("")
        case Some(_) => Forbidden(MSG("login.email.taken"))
      }
    }
  }

  object Rules {

    import play.api.data.validation.{ValidationError => VE}

    val noDigit = """[^0-9]*""".r
    val noUpper = """[^A-Z]*""".r
    val noLower = """[^a-z]*""".r

    def password = Constraint[String]("constraint.password.check") {
      case o if isEmpty(o)   => Invalid(VE("password.too.short", 7))
      case o if o.size <= 7  => Invalid(VE("password.too.short", 7))
      case o if o.size >= 39 => Invalid(VE("password.too.long", 39))
      case noDigit()         => Invalid(VE("password.all_number"))
      case noLower()         => Invalid(VE("password.all_upper"))
      case noUpper()         => Invalid(VE("password.all_lower"))
      case _                 => Valid
    }

    val emailRegex = """[\w\.-]+@[\w\.-]+\.\w+$""".r

    def email = Constraint[String]("constraint.email.check") {
      case o if isEmpty(o)  => Invalid(VE("login.email.empty"))
      case o if o.size > 39 => Invalid(VE("login.email.invalid"))
      case emailRegex()     => Valid
      case _                => Invalid(VE("login.email.invalid"))
    }

    private def isEmpty(s: String) = {
      s == null || s.trim.isEmpty
    }
  }

}