package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.UsersCtrl.{Password, Rules}
import elasticsearch.ElasticSearch
import helpers._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
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
        "original" -> text.verifying(Rules.password),
        "confirmation" -> text
      )(Password.apply)(Password.unapply)
        .verifying("password.not.confirmed", _.isConfirmed)
    )(SignUpFD.apply)(SignUpFD.unapply)
  )

  case class SignUpFD(
    email: EmailAddress,
    password: Password
  )

  def show(id: UUID) =
    UserAction(_.Show).async { implicit req =>
      _users.find(id).map { user =>
        Ok(html.users.show(user))
      }
    }

  def index(pager: Pager) =
    UserAction(_.Index).apply { implicit req =>
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
    val form = Form(single("value" -> text.verifying(Rules.password)))

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

  case class Password(
    original: String,
    confirmation: String
  ) {

    def isConfirmed = original == confirmation
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

    private def isEmpty(s: String) = s == null || s.trim.isEmpty
  }

}