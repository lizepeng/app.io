package controllers

import java.util.UUID

import controllers.Sessions._
import controllers.checkers.AuthCheck
import controllers.session._
import models.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
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
      "email" -> email,
      "password" -> nonEmptyText(minLength = 7).verifying(pwdRule),
      "password_confirmation" -> text
    )(SignUpFD.apply)(SignUpFD.unapply)
      .verifying(
        "Password doesn't match the confirmation",
        fd => fd.password == fd.password_confirmation
      )
  )

  case class SignUpFD(
    email: String,
    password: String,
    password_confirmation: String
  )

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
      formWithErrors => Future.successful {
        BadRequest(html.users.signup(formWithErrors))
      },
      fd => {
        User.findBy(fd.email).flatMap {
          case Some(_) => Future.successful {
            BadRequest(html.users.signup(fm.withGlobalError("Email is invalid or already taken")))
          }
          case None    => {
            val user = User(email = fd.email, password = fd.password)
            User.save(user).flatMap {_ =>
              User.findBy(user.id).map {
                case None    => ServiceUnavailable("")
                case Some(u) => Redirect(routes.Users.show(u.id)).createSession(rememberMe = false)(u)
              }
            }
          }
        }
      }
    )
  }

  val noDigit = """[^0-9]*""".r
  val noUpper = """[^A-Z]*""".r
  val noLower = """[^a-z]*""".r

  def pwdRule = Constraint[String]("constraint.password.check") {
    password =>
      val errors = password match {
        case noDigit() => Seq(ValidationError("Password need at least one number"))
        case noLower() => Seq(ValidationError("Password need at least one lowercase letter"))
        case noUpper() => Seq(ValidationError("Password need at least one uppercase letter"))
        case _         => Nil
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  }
}