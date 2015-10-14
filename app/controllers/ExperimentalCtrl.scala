package controllers

import helpers._
import messages.NotificationActor
import models._
import play.api.i18n.I18nSupport
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Controller
import services.actors.Envelope
import views.html

/**
 * @author zepeng.li@gmail.com
 */
class ExperimentalCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups,
  val _users: Users
)
  extends Controller
  with BasicPlayComponents
  with DefaultPlayExecutor
  with I18nSupport {

  def chat = MaybeUserAction().apply { implicit req =>
    Ok(html.experimental.chat())
  }

  val _notificationRegion = NotificationActor.getRegion(actorSystem)

  def nnew = MaybeUserAction().apply { implicit req =>
    Ok(html.experimental.notification())
  }

  def send = MaybeUserAction().apply { implicit req =>
    req.body.asJson.map { json =>
      (json \ "notify").validate[String].map { notify =>
        _users.all |>>>
          Iteratee.foreach[User] { user =>
            _notificationRegion ! Envelope(user.id, notify)
          }
      }
    }
    Created
  }
}