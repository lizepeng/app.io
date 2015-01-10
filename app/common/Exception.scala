package common

import play.api.Logger
import play.api.i18n.{Lang, Messages}

/**
 * @author zepeng.li@gmail.com
 */
trait BaseException {
  val code: ErrorCode
}

case class NotFoundException(code: ErrorCode) extends BaseException

case class AuthFailedException(code: ErrorCode) extends BaseException

case class ErrorCode(key: String, level: Level) {
  def logMsg(args: Any*) = Messages(s"log.${level.key}.$key", args: _*)

  def msg(args: Any*)(implicit lang: Lang) = Messages(s"msg.${level.key}.$key", args: _*)

  def log(args: Any*)(implicit logger: Logger) = {
    import common.Level._
    val msg = logMsg(args: _*)
    level match {
      case Debug   => logger.debug(msg)
      case Info    => logger.info(msg)
      case Warn    => logger.warn(msg)
      case Error   => logger.error(msg)
      case Severe  => logger.error(msg)
      case _       => logger.warn(msg)
    }
    this
  }
}

case class Level(key: String)

object Level {
  val Debug   = new Level("debug")
  val Info    = new Level("info")
  val Warn    = new Level("warn")
  val Error   = new Level("error")
  val Severe  = new Level("severe")
}

object ErrorCodes {

  import common.Level._

  val UserNotFound  = ErrorCode("not.found.user", Info)
  val WrongPassword = ErrorCode("password.wrong", Info)
}
