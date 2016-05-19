package controllers

import helpers.ExtRequest._
import helpers._
import models._
import models.misc._
import play.api.data.Forms._
import play.api.data._
import play.api.http.HttpConfiguration
import play.api.i18n._
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 *
 **/
class SessionsCtrl(
  val httpConfiguration: HttpConfiguration,
  val cookieSigner: CookieSigner
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups,
  val _userLoginIPs: UserLoginIPs
) extends Controller
  with security.Session
  with BasicPlayComponents
  with UsersComponents
  with AuthenticateBySessionComponents
  with MaybeUserActionComponents
  with ExceptionHandlers
  with DefaultPlayExecutor
  with I18nLoggingComponents
  with I18nSupport {

  val loginFM = Form[LoginFD](
    mapping(
      "email" -> of[EmailAddress],
      "password" -> of[Password],
      "remember_me" -> boolean
    )(LoginFD.apply)(LoginFD.unapply)
  )

  case class LoginFD(
    email: EmailAddress,
    password: Password,
    remember_me: Boolean
  )

  def nnew = MaybeUserAction().apply { implicit req =>
    req.maybeUser match {
      case Success(u) => Redirect(routes.Application.index())
      case _          => Ok(html.account.login(loginFM))
    }
  }

  def create = MaybeUserAction().async { implicit req =>
    val form = loginFM.bindFromRequest
    form.fold(
      failure => Future.successful(BadRequest(html.account.login(failure))),
      success => _users.auth(success.email, success.password).recover {
        case e: User.NotFound      => Logger.warn(e.reason, e); throw User.AuthFailed()
        case e: User.WrongPassword => Logger.warn(e.reason, e); throw User.AuthFailed()
      }.andThen {
        case Success(user) => req.clientIP.map(_userLoginIPs.log(user.id, _))
      }.flatMap { user =>
        Redirect(routes.MyCtrl.dashboard()).createSession(user, success.remember_me)
      }.recover {
        case e: User.AuthFailed => BadRequest(html.account.login(form.withGlobalError(e.message)))
      }
    )
  }

  def destroy = (MaybeUserAction() andThen AuthChecker())
    .async(BodyParsers.parse.empty) { req =>
      Redirect(routes.Application.index()).destroySession(req.user)
    }
}