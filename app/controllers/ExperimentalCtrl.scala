package controllers

import java.util.UUID

import helpers._
import messages._
import models._
import play.api.i18n.I18nSupport
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.mvc.Controller
import services.actors._
import views.html

/**
 * @author zepeng.li@gmail.com
 */
class ExperimentalCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups,
  val _users: Users
) extends Controller
  with CanonicalNamed
  with PackageNameAsCanonicalName
  with BasicPlayComponents
  with MaybeUserActionComponents
  with NotificationRegionComponents
  with DefaultPlayExecutor
  with AkkaTimeOutConfig
  with I18nSupport {

  def chat = MaybeUserAction().apply { implicit req =>
    Ok(html.experimental.chat())
  }

  def showNotification = MaybeUserAction().apply { implicit req =>
    Ok(html.experimental.notification())
  }

  def sendNotification = MaybeUserAction().apply { implicit req =>
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


  val _mailRegion = MailActor.getRegion(actorSystem)

  case class SampleMail(to: UUID, subject: Option[String], text: Option[String])

  object SampleMail {implicit val jsonFormat = Json.format[SampleMail]}

  def showMail = MaybeUserAction().apply { implicit req =>
    Ok(html.experimental.mail())
  }

  def sendMail = MaybeUserAction().apply { implicit req =>
    req.body.asJson.map { json =>
      (json \ "mail").validate[SampleMail].map { sampleMail =>
        _mailRegion ! Envelope(
          sampleMail.to, Mail(
            to = Set(MailTo.User(sampleMail.to)),
            from = sampleMail.to,
            subject = sampleMail.subject.getOrElse("Empty"),
            text = sampleMail.text.getOrElse("Empty")
          )
        )
      }
    }
    Created
  }
}