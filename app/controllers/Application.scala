package controllers

import controllers.helpers.AuthCheck
import controllers.session._
import play.api.mvc._
import views._

object Application extends Controller {

  def index = UserAction {implicit request =>
    Ok(views.html.index())
  }

  def about = UserAction {implicit request =>
    Ok(html.homepages.about())
  }

  def wiki =
    (UserAction andThen AuthCheck) {
      implicit request =>
        Ok(html.homepages.wiki())
    }
}