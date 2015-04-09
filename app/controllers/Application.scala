package controllers

import controllers.session._
import helpers.AppConfig
import models.Schemas
import models.cfs.Path
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import views._

object Application
  extends MVModule("app") with Controller
  with AppConfig {

  def index = UserAction { implicit req =>
    Ok(html.welcome.index())
  }

  def about = UserAction { implicit req =>
    Ok(html.static_pages.about())
  }

  def wiki = UserAction { implicit req =>
    val videoPath = config.getString("wiki.video").map(Path(_))
    Ok(html.static_pages.wiki(videoPath))
  }

  def recreate = UserAction.async {
    Schemas.create.map { _ => Ok("Schema Created") }
  }

}