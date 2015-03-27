package controllers

import controllers.session._
import helpers.{BaseException, Logging}
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import views._

import scala.concurrent.Future
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 *
 **/
object Sessions extends Controller with session.Session with Logging {

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

  def nnew = UserAction { implicit req =>
    req.user match {
      case Some(u) => Redirect(routes.Application.index())
      case None    => Ok(html.account.login(loginFM))
    }
  }

  def create = UserAction.async { implicit req =>
    val form = loginFM.bindFromRequest
    form.fold(
      failure => Future.successful(BadRequest(html.account.login(failure))),
      success => User.auth(success.email, success.password).andThen {
        case Failure(e: User.NotFound)      => Logger.info(e.reason)
        case Failure(e: User.WrongPassword) => Logger.info(e.reason)
      }.map { implicit user =>
        Redirect(routes.Users.show(user.id)).createSession(success.remember_me)
      }.recover { case e: BaseException =>
        BadRequest(html.account.login(form.withGlobalError(e.message)))
      }
    )
  }

  def destroy = Action {
    Redirect(routes.Application.index()).destroySession
  }

}