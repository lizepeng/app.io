package helpers

import play.api.i18n._

import scala.collection.mutable

/**
 * @author zepeng.li@gmail.com
 */
trait Logging {
  self =>

  implicit lazy val Logger = self match {
    case s: CanonicalNamed => play.api.Logger(s.canonicalName)
    case _                 => play.api.Logger(this.getClass)
  }
}

case class LoggingMessages(self: Messages) extends AnyVal

trait I18nLogging extends Logging {

  implicit def loggingMessages: LoggingMessages
}

trait I18nLoggingComponents extends I18nLogging {

  def messagesApi: MessagesApi

  def langs: Langs

  implicit lazy val loggingMessages = LoggingMessages(messagesApi.preferred(langs.availables))
}

trait Loggable extends Product {

  def code: String

  def message(implicit messages: Messages): String = generate("msg")(messages)

  def reason(implicit messages: LoggingMessages): String = generate("log")(messages.self)

  private def generate(key: String)(implicit messages: Messages): String = {
    messages(
      s"$key.$code",
      productIterator.map(_.toString).toList: _*
    )
  }
}

trait TimeLogging extends Logging {

  private      val start   = System.currentTimeMillis
  private lazy val metrics = mutable.Map[String, Long]()

  def timeOneStep(metric: String, previous: Long) = {
    if (!Logger.isDebugEnabled) ""
    else {
      val current = System.currentTimeMillis
      val delta = current - previous
      metrics.update(metric, delta + metrics.getOrElse(metric, 0L))
      s"$metric >> $delta ms"
    }
  }

  def timeSinceStart(metric: String) = {
    if (!Logger.isDebugEnabled) ""
    else s"$metric >>>> ${metrics.getOrElse(metric, 0L)} ms"
  }

  def timeSinceStart = {
    if (!Logger.isDebugEnabled)
      s"spent ${System.currentTimeMillis - start} ms"
  }

  def logTime[A](msg: => String = "", metric: String = "")(op: => A): A = {
    if (!Logger.isDebugEnabled) op
    else {
      val previous = System.currentTimeMillis
      val ret = op
      Logger.debug(s"$msg, ${timeOneStep(metric, previous)}")
      ret
    }
  }
}