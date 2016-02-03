package controllers

import helpers.ExtRequest._
import helpers._
import models._
import models.misc._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n._
import play.api.mvc._
import views._

import scala.concurrent.Future
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 *
 **/
class SessionsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups,
  val _userLoginIPs: UserLoginIPs
)
  extends Controller
  with security.Session
  with BasicPlayComponents
  with UsersComponents
  with DefaultPlayExecutor
  with I18nSupport
  with Logging {

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
      case Some(u) => Redirect(routes.Application.index())
      case None    => Ok(html.account.login(loginFM))
    }
  }

  def create = MaybeUserAction().async { implicit req =>
    val form = loginFM.bindFromRequest
    form.fold(
      failure => Future.successful(BadRequest(html.account.login(failure))),
      success => _users.auth(success.email, success.password).recover {
        case e: User.NotFound      => Logger.warn(e.reason); throw User.AuthFailed()
        case e: User.WrongPassword => Logger.warn(e.reason); throw User.AuthFailed()
      }.andThen {
        case Success(user) => req.clientIP.map(_userLoginIPs.log(user.id, _))
      }.map { implicit user =>
        Redirect(routes.MyCtrl.dashboard()).createSession(success.remember_me)
      }.recover { case e: BaseException =>
        BadRequest(html.account.login(form.withGlobalError(e.message)))
      }
    )
  }

  def destroy = Action {
    Redirect(routes.Application.index()).destroySession
  }

}