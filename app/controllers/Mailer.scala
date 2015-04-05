package controllers

import akka.actor.Cancellable
import helpers.{AppConfig, Logging}
import models.{EmailTemplate, User}
import play.api.Play.current
import play.api.i18n.{Messages => MSG}
import play.api.libs.mailer._
import plugins.MAMailerPlugin

/**
 * @author zepeng.li@gmail.com
 */
object Mailer extends Logging with AppConfig {

  override lazy val module_name = "controllers.mailer"

  private lazy  val admins      = config
    .getStringSeq("admin.email-addresses")
    .getOrElse(Seq.empty)

  def schedule(
    mailer: String,
    tmpl: EmailTemplate,
    user: User,
    args: (String, Any)*): Cancellable = MAMailerPlugin(mailer).schedule {
    val args2: Seq[(String, Any)] =
      args ++ Seq(
        "user.name" -> user.name,
        "user.email" -> user.email
      )

    if (tmpl.subject.isEmpty || tmpl.text.isEmpty) {
      genAlertMail(s"Email Template ${tmpl.id} is empty!!!")
    }
    else {
      Email(
        subject = substitute(tmpl.subject, args2: _*),
        from = "",
        to = Seq(s"${user.name} <${user.email}>"),
        bodyText = Some(substitute(tmpl.text, args2: _*))
      )
    }
  }

  def genAlertMail(text: String) = Email(
    subject = "Warning",
    from = "",
    to = Seq(s"Administrators ${admins.map("<" + _ + ">").mkString(",")}"),
    bodyText = Some(
      s"""
         |$text
      """.stripMargin
    )
  )

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