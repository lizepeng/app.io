package helpers

import scala.collection.mutable

/**
 * @author zepeng.li@gmail.com
 */
trait Logging {
  //be careful, this can not be used with inner class above two level
  implicit val Logger = play.api.Logger(s"${this.getClass.getCanonicalName}")
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