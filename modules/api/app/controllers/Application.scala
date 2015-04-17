package controllers.api

import play.api.mvc._

object Application extends Controller {

  def index = Action { implicit req =>
    Ok("api")
  }
}