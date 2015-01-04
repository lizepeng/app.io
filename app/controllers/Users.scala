package controllers

import java.util.UUID

import controllers.Sessions._
import controllers.helpers.AuthCheck
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
      "email" -> text.verifying(Rules.email),
      "password" -> text.verifying(Rules.password),
      "password_confirmation" -> text
    )(SignUpFD.apply)(SignUpFD.unapply)
      .verifying(
        "password.not.confirmed",
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
            BadRequest(html.users.signup(fm.withGlobalError("login.email.taken")))
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

  object Rules {

    import play.api.data.validation.{ValidationError => VE}

    val noDigit = """[^0-9]*""".r
    val noUpper = """[^A-Z]*""".r
    val noLower = """[^a-z]*""".r

    def password = Constraint[String]("constraint.password.check") {
      case p if isEmpty(p) => Invalid(VE("password.empty"))
      case noDigit()       => Invalid(VE("password.all_number"))
      case noLower()       => Invalid(VE("password.all_upper"))
      case noUpper()       => Invalid(VE("password.all_lower"))
      case _               => Valid
    }

    val emailRegex = """\w+@\w+.\w+$""".r

    def email = Constraint[String]("constraint.email.check") {
      case s if isEmpty(s) => Invalid(VE("login.email.empty"))
      case emailRegex()    => Valid
      case _               => Invalid(VE("login.email.invalid"))
    }

    private def isEmpty(s: String) = {
      s == null || s.trim.isEmpty
    }
  }

}