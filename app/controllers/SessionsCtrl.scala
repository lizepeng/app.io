package controllers

import helpers._
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 *
 **/
class SessionsCtrl(
  val basicPlayApi: BasicPlayApi
)(
  implicit
  val userRepo: Users
)
  extends Controller
  with security.Session
  with BasicPlayComponents
  with I18nSupport
  with Logging {

  val loginFM = Form[LoginFD](
    mapping(
      "email" -> text,
      "password" -> text,
      "remember_me" -> boolean
    )(LoginFD.apply)(LoginFD.unapply)
  )

  case class LoginFD(
    email: String,
    password: String,
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
      success => userRepo.auth(success.email, success.password).recover {
        case e: models.User.NotFound      => Logger.warn(e.reason); throw models.User.AuthFailed()
        case e: models.User.WrongPassword => Logger.warn(e.reason); throw models.User.AuthFailed()
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