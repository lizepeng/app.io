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

  def nnew = UserAction {implicit request =>
    request.user match {
      case Some(u) => Redirect(routes.Application.index())
      case None    => Ok(html.account.login(loginFM))
    }
  }

  def create = UserAction.async {implicit request =>
    val fm = loginFM.bindFromRequest
    fm.fold(
      fm => Future.successful(BadRequest(html.account.login(fm))),
      fd => User.auth(fd.email, fd.password).andThen {
        case Failure(ex: UserNotFound)  => Logger.info(ex.reason)
        case Failure(ex: WrongPassword) => Logger.info(ex.reason)
      }.map {implicit user =>
        Redirect(routes.Users.show(user.id)).createSession(fd.remember_me)
      }.recover {case ex: BaseException =>
        BadRequest(html.account.login(fm.withGlobalError(ex.message)))
      }
    )
  }

  def destroy = Action {
    Redirect(routes.Application.index()).destroySession
  }

}