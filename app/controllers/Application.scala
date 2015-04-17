package controllers

import controllers.api.MVModule
import helpers.AppConfig
import models.Schemas
import models.cfs.Path
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import security._
import views._

object Application
  extends MVModule("app") with Controller
  with AppConfig {

  def index = MaybeUserAction { implicit req =>
    Ok(html.welcome.index())
  }

  def about = MaybeUserAction { implicit req =>
    Ok(html.static_pages.about())
  }

  def wiki = MaybeUserAction { implicit req =>
    val videoPath = config.getString("wiki.video").map(Path(_))
    Ok(html.static_pages.wiki(videoPath))
  }

  def recreate = MaybeUserAction.async {
    Schemas.create.map { _ => Ok("Schema Created") }
  }

}