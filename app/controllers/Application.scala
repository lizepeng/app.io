package controllers

import java.util.UUID

import _root_.common.AppConfig
import controllers.session._
import models.cfs.CFS
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
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

  def wiki = UserAction.async {implicit request =>
    val id = config.getString("wiki.video.id").getOrElse("")
    CFS.file.findBy(UUID.fromString(id)).map {
      video => Ok(html.static_pages.wiki(video))
    }
  }
}