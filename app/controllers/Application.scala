package controllers

import controllers.session._
import helpers.{AppConfig, Logging}
import models.Schemas
import models.cfs.Path
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import views._

object Application extends Controller with Logging with AppConfig {

  override def module_name: String = "app"

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