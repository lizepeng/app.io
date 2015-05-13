package controllers

import batches.ReIndex
import elasticsearch.ES
import helpers.{AppConfig, ModuleLike}
import models._
import models.cfs.Path
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import security._
import views._

object Application
  extends Controller
  with ModuleLike with ViewMessages with AppConfig {

  override val moduleName = "app"

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

  def recreate = MaybeUserAction.async {
    Schemas.create.map { _ => Ok("Schema Created") }
  }

  def reindex = MaybeUserAction {
    new ReIndex[Group](
      Group.all,
      list => (ES.BulkIndex(list) into Group)
        .map { res => Logger.info(res.getTook.toString) }
    )(10).start()

    new ReIndex[User](
      User.all,
      list => (ES.BulkIndex(list) into User)
        .map { res => Logger.info(res.getTook.toString) }
    )(10).start()

    new ReIndex[AccessControl](
      AccessControl.all,
      list => (ES.BulkIndex(list) into AccessControl)
        .map { res => Logger.info(res.getTook.toString) }
    )(10).start()
    Ok("started")
  }
}