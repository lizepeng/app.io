package services

import akka.actor.{ActorSystem, Cancellable}
import helpers._
import models._
import org.apache.commons.mail.{HtmlEmail, MultiPartEmail}
import play.api._
import play.api.i18n._
import play.api.libs.mailer._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
class MailService(
  val _basicPlayApi: BasicPlayApi
)
  extends CanonicalNamed
  with BasicPlayComponents
  with AppConfig
  with I18nSupport {

  override val basicName: String = "mailer"

  val plugin = new MAMailerPlugin(configuration, actorSystem)

  private lazy val admins = config
    .getStringSeq("admin.email-addresses")
    .getOrElse(Seq.empty)

  def schedule(
    mailer: String,
    tmpl: EmailTemplate,
    user: User,
    args: (String, Any)*
  ): Cancellable = plugin.instance(mailer).schedule {
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
      case msgPattern(n)                    => messagesApi(n)
      case argPattern(n) if map.contains(n) => map.get(n).get.toString
      case _                                => "#{???}"
    })
  }
}

class MAMailerPlugin(
  configuration: Configuration,
  actorSystem: ActorSystem
) extends Logging {

  val executor: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.mailer")

  private lazy val mailerConf = getSubConfig(configuration, "mailer")
  private lazy val instances  = mailerConf.subKeys.map { key =>
    val conf = getSubConfig(mailerConf, key)
    Logger.info(s"Created mailer: $key.")
    (key, MAMailer(
      mailerInstance(conf),
      conf.getString("smtp.user").getOrElse("")
    )(actorSystem, executor))
  }.toMap

  def instance(key: String): MAMailer = instances(key)

  private def mailerInstance(conf: Configuration): MailerClient = {

    val smtpHost = conf.getString("smtp.host").getOrElse {
      throw new RuntimeException("key.smtp.host needs to be set in application.conf in order to use this plugin (or set smtp.mock to true)")
    }
    val smtpPort = conf.getInt("smtp.port").getOrElse(25)
    val smtpSsl = conf.getBoolean("smtp.ssl").getOrElse(false)
    val smtpTls = conf.getBoolean("smtp.tls").getOrElse(false)
    val smtpUser = conf.getString("smtp.user")
    val smtpPassword = conf.getString("smtp.password")
    val debugMode = conf.getBoolean("smtp.debug").getOrElse(false)
    val smtpTimeout = conf.getInt("smtp.timeout")
    val smtpConnectionTimeout = conf.getInt("smtp.connection.timeout")

    new SMTPMailer(smtpHost, smtpPort, smtpSsl, smtpTls, smtpUser, smtpPassword, debugMode, smtpTimeout, smtpConnectionTimeout) {
      override def send(email: MultiPartEmail): String = email.send()

      override def createMultiPartEmail(): MultiPartEmail = new MultiPartEmail()

      override def createHtmlEmail(): HtmlEmail = new HtmlEmail()
    }
  }

  private def getSubConfig(conf: Configuration, key: String): Configuration = {
    conf.getConfig(key).getOrElse(Configuration.empty)
  }
}

case class MAMailer(api: MailerClient, smtp_user: String)(
  implicit actorSystem: ActorSystem, executor: ExecutionContext
) {

  def schedule(email: Email)(
    implicit messages: Messages
  ): Cancellable = {
    actorSystem.scheduler.scheduleOnce(1.second) {
      val id = api.send {
        email.copy(from = s"${messages("app.name")} <$smtp_user>")
      }
      Logger.trace(s"Email:[$id] has been sent")
    }
  }
}