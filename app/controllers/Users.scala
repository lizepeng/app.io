package controllers

import java.util.UUID

import controllers.Sessions._
import controllers.session._
import models.{InternalGroups, User}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import security.AuthCheck
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Users extends Controller {

  val signUpFM = Form[SignUpFD](
    mapping(
      "email" -> text.verifying(Rules.email),
      "password" -> mapping(
        "original" -> text.verifying(Rules.password),
        "confirmation" -> text
      )(Password.apply)(Password.unapply)
        .verifying("password.not.confirmed", _.isConfirmed)
    )(SignUpFD.apply)(SignUpFD.unapply)
  )

  case class SignUpFD(
    email: String,
    password: Password
  )

  case class Password(
    original: String,
    confirmation: String
  ) {
    def isConfirmed = original == confirmation
  }

  def show(id: UUID) =
    (UserAction >> AuthCheck) { implicit req =>
      Ok(html.users.show())
    }

  def index = TODO

  def nnew = UserAction { implicit req =>
    req.user match {
      case None    => Ok(views.html.users.signup(signUpFM))
      case Some(u) => Redirect(routes.Users.show(u.id))
    }
  }

  def create = UserAction.async { implicit req =>
    val bound = signUpFM.bindFromRequest
    bound.fold(
      failure => Future.successful {
        BadRequest {
          html.users.signup {
            if (failure.hasGlobalErrors) failure
            else failure.withGlobalError("signup.failed")
          }
        }
      },
      success => User.find(success.email).map { _ =>
        BadRequest {
          html.users.signup {
            bound.withGlobalError("signup.failed")
              .withError("email", "login.email.taken")
          }
        }
      }.recoverWith {
        case e: User.NotFound =>
          User.save(
            User(
              email = success.email,
              password = success.password.original,
              internal_groups = InternalGroups(1)
            )
          ).map {
            implicit u =>
              Redirect {
                routes.Users.show(u.id)
              }.createSession(rememberMe = false)
          }
      }
    )
  }

  def checkEmail = Action.async { implicit req =>
    val form = Form(single("value" -> text.verifying(Rules.email)))

    form.bindFromRequest().fold(
      failure => Future.successful(Forbidden(failure.errorsAsJson)),
      success => User.find(success).map { _ =>
        Forbidden(Json.obj("value" -> MSG("login.email.taken")))
      }.recover {
        case e: User.NotFound => Ok("")
      }
    )
  }

  def checkPassword = Action { implicit req =>
    val form = Form(single("value" -> text.verifying(Rules.password)))

    form.bindFromRequest().fold(
      failure => Forbidden(failure.errorsAsJson),
      success => Ok("")
    )
  }

  object Rules {

    import play.api.data.validation.{ValidationError => VE}

    private val noDigit = """[^0-9]*""".r
    private val noUpper = """[^A-Z]*""".r
    private val noLower = """[^a-z]*""".r

    def password = Constraint[String]("constraint.password.check") {
      case o if isEmpty(o)     => Invalid(VE("password.too.short", 7))
      case o if o.length <= 7  => Invalid(VE("password.too.short", 7))
      case o if o.length >= 39 => Invalid(VE("password.too.long", 39))
      case noDigit()           => Invalid(VE("password.all_number"))
      case noLower()           => Invalid(VE("password.all_upper"))
      case noUpper()           => Invalid(VE("password.all_lower"))
      case _                   => Valid
    }

    private val emailRegex = """[\w\.-]+@[\w\.-]+\.\w+$""".r

    def email = Constraint[String]("constraint.email.check") {
      case o if isEmpty(o)    => Invalid(VE("login.email.empty"))
      case o if o.length > 39 => Invalid(VE("login.email.invalid"))
      case emailRegex()       => Valid
      case _                  => Invalid(VE("login.email.invalid"))
    }

    private def isEmpty(s: String) = s == null || s.trim.isEmpty
  }

}