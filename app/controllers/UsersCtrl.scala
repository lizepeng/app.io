package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import elasticsearch._
import helpers._
import models._
import models.misc._
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.HttpConfiguration
import play.api.i18n._
import play.api.libs.crypto.CookieSigner
import play.api.libs.json.Json
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
class UsersCtrl(
  val httpConfiguration: HttpConfiguration,
  val cookieSigner: CookieSigner
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch
) extends UserCanonicalNamed
  with CheckedModuleName
  with Controller
  with security.Session
  with BasicPlayComponents
  with AuthenticateBySessionComponents
  with UserActionRequiredComponents
  with MaybeUserActionComponents
  with UserActionComponents[UsersCtrl.AccessDef]
  with UsersCtrl.AccessDef
  with ExceptionHandlers
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
      )(Password.Confirmation.apply)(Password.Confirmation.unapply)
        .verifying("password.not.confirmed", _.isConfirmed)
    )(SignUpFD.apply)(SignUpFD.unapply)
  )

  case class SignUpFD(
    email: EmailAddress,
    password: Password.Confirmation
  )

  def show(id: UUID) =
    UserAction(_.P02).async { implicit req =>
      for {
        user <- _users.find(id)
        grps <- _groups.find(_internalGroups.InternalGroupIds)
      } yield {
        Ok(html.users.show(user, grps))
      }
    }

  def index(pager: Pager, sort: Seq[SortField]) = {
    val default = _users.sorting(_.email.asc)
    UserAction(_.P03, _.P01).apply { implicit req =>
      Ok(html.users.index(pager, if (sort.nonEmpty) sort else default))
    }
  }

  def nnew = MaybeUserAction().apply { implicit req =>
    req.maybeUser match {
      case Success(u) => Redirect(routes.MyCtrl.dashboard())
      case _          => Ok(views.html.users.signup(signUpFM))
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
      } yield saved).flatMap { case saved =>
        Redirect {
          routes.MyCtrl.dashboard()
        }.createSession(saved, rememberMe = false)
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
        Ok
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
      success => Ok
    )
  }

}

object UsersCtrl
  extends UserCanonicalNamed
    with PermissionCheckable
    with CanonicalNameBasedMessages
    with ViewMessages {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    /** Access : Upload Profile Image */
    val P16 = Access.Pos(16)

    def values = Seq(P01, P02, P03, P16)
  }

  object AccessDef extends AccessDef
}