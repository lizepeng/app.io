package services

import akka.actor._
import helpers._
import models._
import play.api._
import play.api.i18n._
import play.api.libs.mailer._

import scala.concurrent._
import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
class MailService(
  val basicPlayApi: BasicPlayApi
) extends MailServiceCanonicalNamed
  with BasicPlayComponents
  with AppConfigComponents
  with I18nSupport {

  val plugin = new MAMailerPlugin(configuration, actorSystem)

  private lazy val admins = config
    .getStringSeq("admin.email-addresses")
    .getOrElse(Seq.empty)

  def sendTo(user: User)(
    mailer: String,
    tmpl: EmailTemplate,
    args: Map[String, Any] = Map()
  ): Future[String] = send(
    mailer, tmpl, args ++ Seq(
      "to.user_name" -> user.user_name,
      "to.email" -> user.email
    )
  )

  def send(
    mailer: String, tmpl: EmailTemplate, args: Map[String, Any] = Map()
  ): Future[String] = plugin.instance(mailer).send {
    buildMail(tmpl, args)
  }

  def schedule(
    mailer: String, tmpl: EmailTemplate, args: Map[String, Any] = Map()
  ): Cancellable = plugin.instance(mailer).schedule {
    buildMail(tmpl, args)
  }

  def buildMail(tmpl: EmailTemplate, args: Map[String, Any]): Email = {
    if (tmpl.invalid) {
      genAlertMail(s"Email Template ${tmpl.id} is empty!!!")
    } else {
      Email(
        subject = substitute(tmpl.subject, args),
        from = "",
        to = Seq(s"${substitute(tmpl.to, args)} <${args("to.email")}>"),
        bodyText = Some(substitute(tmpl.text, args))
      )
    }
  }

  def genAlertMail(text: String) = Email(
    subject = "Warning",
    from = "",
    to = Seq(s"Administrators ${admins.map("<" + _ + ">").mkString(",")}"),
    bodyText = Some(text)
  )

  val anyPattern = """[\$|#]\{([\.\w]+)\}""".r
  val msgPattern = """\$\{([\.\w]+)\}""".r
  val argPattern = """#\{([\.\w]+)\}""".r

  def substitute(text: String, args: Map[String, Any]) = {
    anyPattern replaceAllIn(text, _ match {
      case msgPattern(n)                     => messagesApi(n)
      case argPattern(n) if args.contains(n) => args.get(n).get.toString
      case _                                 => "#{???}"
    })
  }
}

object MailService extends MailServiceCanonicalNamed

trait MailServiceCanonicalNamed extends CanonicalNamed {

  override val basicName: String = "mailer"
}

class MAMailerPlugin(
  val configuration: Configuration,
  val actorSystem: ActorSystem
) extends ConfiguredExecutor
  with Logging {

  val executor: ExecutionContext = lookupExecutionContext("contexts.mailer")

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

    val smtpConfig = SMTPConfiguration(
      host = conf.getString("smtp.host").getOrElse {
        throw new RuntimeException("key.smtp.host needs to be set in application.conf in order to use this plugin (or set smtp.mock to true)")
      },
      port = conf.getInt("smtp.port").getOrElse(25),
      ssl = conf.getBoolean("smtp.ssl").getOrElse(false),
      tls = conf.getBoolean("smtp.tls").getOrElse(false),
      user = conf.getString("smtp.user"),
      password = conf.getString("smtp.password"),
      debugMode = conf.getBoolean("smtp.debug").getOrElse(false),
      timeout = conf.getInt("smtp.timeout"),
      connectionTimeout = conf.getInt("smtp.connection.timeout")
    )

    new SMTPMailer(smtpConfig)
  }

  private def getSubConfig(conf: Configuration, key: String): Configuration = {
    conf.getConfig(key).getOrElse(Configuration.empty)
  }
}

case class MAMailer(api: MailerClient, smtp_user: String)(
  implicit actorSystem: ActorSystem, ec: ExecutionContext
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

  def send(email: Email)(
    implicit messages: Messages
  ) = Future(
    api.send {
      email.copy(from = s"${messages("app.name")} <$smtp_user>")
    }
  )
}