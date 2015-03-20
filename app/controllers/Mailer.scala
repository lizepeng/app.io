package controllers

import helpers.Contexts.mailerContext
import helpers.Logging
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.mailer._

import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
object Mailer extends Logging {

  private lazy val smtp_user = current.configuration.getString("smtp.user")

  def schedule(email: Email) = {
    Akka.system.scheduler.scheduleOnce(1.second) {
      smtp_user match {
        case Some(su) =>
          val id = MailerPlugin.send {
            email.copy(from = s"App.io <$su>")
          }
          Logger.trace(s"$id has been sent")
        case None     =>
          Logger.error("smtp server is not configured yet")
      }
    }
  }
}