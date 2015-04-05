package plugins

import akka.actor.Cancellable
import org.apache.commons.mail.{HtmlEmail, MultiPartEmail}
import play.api.Play.current
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Akka
import play.api.libs.mailer._
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
class MultiAccountsMailerPlugin(app: play.api.Application) extends MAMailerPlugin {

  private lazy val appConfig  = app.configuration
  private lazy val mailerConf = getSubConfig(appConfig, "mailer")
  private lazy val instances  = mailerConf.subKeys.map { key =>
    val conf = getSubConfig(mailerConf, key)
    (key, MAMailer(
      mailerInstance(conf),
      conf.getString("smtp.user").getOrElse("")
    ))
  }.toMap

  override lazy val enabled = !appConfig.getString("multi.accounts.mailer.plugin").filter(_ == "disabled").isDefined

  override def onStart() {
    instances
    Logger.info("Multiple Accounts Mailer Plugin has started")
  }

  override def instance(key: String): MAMailer = instances(key)

  private def mailerInstance(conf: Configuration): MailerAPI = {

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

    new CommonsMailer(smtpHost, smtpPort, smtpSsl, smtpTls, smtpUser, smtpPassword, debugMode, smtpTimeout, smtpConnectionTimeout) {
      override def send(email: MultiPartEmail): String = email.send()

      override def createMultiPartEmail(): MultiPartEmail = new MultiPartEmail()

      override def createHtmlEmail(): HtmlEmail = new HtmlEmail()
    }
  }

  private def getSubConfig(conf: Configuration, key: String): Configuration = {
    conf.getConfig(key).getOrElse(Configuration.empty)
  }
}

/**
 * plugin access
 */
object MAMailerPlugin {

  def apply(key: String)(implicit app: play.api.Application): MAMailer =
    app.plugin(classOf[MAMailerPlugin]).get.instance(key)

  implicit val mailerContext: ExecutionContext =
    Akka.system.dispatchers.lookup("mailer-context")
}

/**
 * plugin interface
 */
trait MAMailerPlugin extends play.api.Plugin {

  def instance(key: String): MAMailer
}

case class MAMailer(api: MailerAPI, smtp_user: String) {

  import MAMailerPlugin.mailerContext

  def schedule(email: Email): Cancellable = {
    Akka.system.scheduler.scheduleOnce(1.second) {
      val id = api.send {
        email.copy(from = s"${MSG("app.name")} <$smtp_user>")
      }
      Logger.trace(s"Email:[$id] has been sent")
    }
  }
}