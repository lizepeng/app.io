package controllers

import java.util.UUID

import controllers.session._
import models.cfs.CFS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views._

object Application extends Controller {

  def index = UserAction {implicit request =>
    Ok(views.html.index())
  }

  def about = UserAction {implicit request =>
    Ok(html.homepages.about())
  }

  def wiki = UserAction.async {implicit request =>
    CFS.file.find(UUID.fromString("e5280530-9366-11e4-967d-d3c54ab016d4")).map {
      video => Ok(html.homepages.wiki(video))
    }
  }
}