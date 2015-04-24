package controllers

import java.util.UUID

import controllers.Sessions._
import controllers.api.SecuredController
import helpers._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Users
  extends SecuredController(User)
  with ViewMessages {

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
    PermCheck(_.Show).async { implicit req =>
      User.find(id).map { user =>
        Ok(html.users.show(user))
      }
    }

  def index(pager: Pager) =
    PermCheck(_.Index).async { implicit req =>
      User.list(pager).map { page =>
        Ok(html.users.index(page))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def nnew = MaybeUserAction { implicit req =>
    req.maybeUser match {
      case None    => Ok(views.html.users.signup(signUpFM))
      case Some(u) => Redirect(routes.My.dashboard())
    }
  }

  def create = MaybeUserAction.async { implicit req =>
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
      success => User.checkEmail(success.email).flatMap { _ =>
        User(
          email = success.email,
          password = success.password.original
        ).save.map { u =>
          Redirect {
            routes.My.dashboard()
          }.createSession(rememberMe = false)(u)
        }
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
    val form = Form(single("value" -> text.verifying(Rules.email)))

    form.bindFromRequest().fold(
      failure => Future.successful(Forbidden(failure.errorsAsJson)),
      success => User.checkEmail(success).map { _ =>
        Ok("")
      }.recover {
        case e: User.EmailTaken =>
          Forbidden(Json.obj("value" -> e.message))
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
      case o if isEmpty(o)    => Invalid(VE("email.empty"))
      case o if o.length > 39 => Invalid(VE("email.invalid"))
      case emailRegex()       => Valid
      case _                  => Invalid(VE("email.invalid"))
    }

    private def isEmpty(s: String) = s == null || s.trim.isEmpty
  }

}