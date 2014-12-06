package controllers

import controllers.session._
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import views._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 *
 **/
object Sessions extends Controller with session.Session {

  val loginFM = Form[LoginFD](
    mapping(
      "email" -> email,
      "password" -> text.verifying(nonEmpty),
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
      formWithErrors => Future {
        BadRequest(html.account.login(formWithErrors))
      },
      fd =>
        User.auth(fd.email, fd.password).flatMap {eitherUser =>
          eitherUser.fold(
            ex => Future {
              BadRequest(html.account.login(fm.withGlobalError(ex.code.msg())))
            }
            ,
            implicit user => Future {
              Redirect(routes.Users.show(user.id)).createSession(fd.remember_me)
            }
          )
        }
    )
  }

  def destroy = Action {
    Redirect(routes.Application.index()).destroySession
  }

}