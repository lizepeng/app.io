package controllers

import helpers._
import models.cfs.Path
import play.api.i18n.I18nSupport
import play.api.mvc.Controller
import security._
import views._

class Application(
  val basicPlayApi: BasicPlayApi
)
  extends Controller
  with CanonicalNamed
  with ViewMessages
  with AppConfig
  with BasicPlayComponents
  with I18nSupport {

  override val basicName = "app"

  def index = MaybeUserAction { implicit req =>
    Ok(html.welcome.index())
  }

  def about = MaybeUserAction { implicit req =>
    Ok(html.static_pages.about())
  }

  def wiki = MaybeUserAction { implicit req =>
    val videoPath = config.getString("wiki.video").map(fn => Path(filename = Some(fn)))
    Ok(html.static_pages.wiki(videoPath))
  }
}