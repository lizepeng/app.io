package helpers

import play.api.i18n._

import scala.collection.mutable

/**
 * @author zepeng.li@gmail.com
 */
trait Logging {

  implicit lazy val Logger =
    play.api.Logger(
      if (loggerName.nonEmpty) loggerName
      //be careful, this can not be used with inner class above two level
      else
        Option(this.getClass.getCanonicalName).getOrElse(this.getClass.getName)
    )

  def loggerName: String = ""
}

trait Loggable extends Product {

  def code: String

  def message(
    implicit messages: Messages
  ): String = generate("msg")

  def reason(
    implicit langs: Langs, messagesApi: MessagesApi
  ): String = generate("log")(
    Messages(
      langs.availables.headOption.getOrElse(Lang.defaultLang),
      messagesApi
    )
  )

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