package controllers

import akka.actor.Cancellable
import helpers.Contexts.mailerContext
import helpers.Logging
import models.{EmailTemplate, User}
import play.api.Play.current
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Akka
import play.api.libs.mailer._

import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
object Mailer extends Logging {

  private lazy val smtp_user = current.configuration.getString("smtp.user")

  def schedule(email: Email): Cancellable = {
    Akka.system.scheduler.scheduleOnce(1.second) {
      smtp_user match {
        case Some(su) =>
          val id = MailerPlugin.send {
            email.copy(from = s"${MSG("app.name")} <$su>")
          }
          Logger.trace(s"Email:[$id] has been sent")
        case None     =>
          Logger.error("smtp server is not configured yet")
      }
    }
  }

  def schedule(
    tmpl: EmailTemplate,
    user: User,
    args: (String, Any)*): Cancellable = schedule {
    val args2: Seq[(String, Any)] =
      args ++ Seq(
        "user.name" -> user.name,
        "user.email" -> user.email
      )

    Email(
      subject = substitute(tmpl.subject, args2: _*),
      from = "",
      to = Seq(s"${user.name} <${user.email}>"),
      bodyText = Some(substitute(tmpl.text, args2: _*))
    )
  }

  val anyPattern = """[\$|#]\{([\.\w]+)\}""".r
  val msgPattern = """\$\{([\.\w]+)\}""".r
  val argPattern = """#\{([\.\w]+)\}""".r

  def substitute(text: String, args: (String, Any)*) = {
    val map = args.toMap
    anyPattern replaceAllIn(text, _ match {
      case msgPattern(n)                    => MSG(n)
      case argPattern(n) if map.contains(n) => map.get(n).get.toString
      case _                                => "#{???}"
    })
  }
}