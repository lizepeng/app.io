package controllers

import _root_.helpers.AppConfig
import controllers.session._
import models.Schemas
import models.cfs.Path
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import views._

object Application extends Controller with AppConfig {

  override def config_key: String = "app"

  def index = UserAction {implicit request =>
    Ok(html.welcome.index())
  }

  def about = UserAction {implicit request =>
    Ok(html.static_pages.about())
  }

  def wiki = UserAction {implicit request =>
    val videoPath = config.getString("wiki.video").map(Path(_))
    Ok(html.static_pages.wiki(videoPath))
  }

  def recreate = UserAction.async {
    Schemas.create.map {_ => Ok("Schema Created")}
  }

}