package controllers

import helpers._
import models.Groups
import models.cfs.Path
import play.api.i18n.I18nSupport
import play.api.mvc.Controller
import views._

class Application(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups
)
  extends Controller
  with BasicPlayComponents
  with I18nSupport
  with CanonicalNamed
  with ViewMessages
  with AppConfigComponents {

  override val basicName = "app"

  def index = MaybeUserAction().apply { implicit req =>
    Ok(html.welcome.index())
  }

  def about = MaybeUserAction().apply { implicit req =>
    Ok(html.static_pages.about())
  }

  def wiki = MaybeUserAction().apply { implicit req =>
    val videoPath = config.getString("wiki.video").map(fn => Path(filename = Some(fn)))
    Ok(html.static_pages.wiki(videoPath))
  }
}