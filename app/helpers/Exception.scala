package helpers

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

case class ErrorCode(key: String, level: ErrorCode.Level) {
  def logMsg(args: Any*) = Messages(s"log.${level.key}.$key", args: _*)

  def msg(args: Any*)(implicit lang: Lang) = Messages(s"msg.${level.key}.$key", args: _*)

  def log(args: Any*)(implicit logger: Logger) = {
    val msg = logMsg(args: _*)
    level match {
      case ErrorCode.Level.Debug  => logger.debug(msg)
      case ErrorCode.Level.Info   => logger.info(msg)
      case ErrorCode.Level.Warn   => logger.warn(msg)
      case ErrorCode.Level.Error  => logger.error(msg)
      case ErrorCode.Level.Severe => logger.error(msg)
      case _                      => logger.warn(msg)
    }
    this
  }
}

object ErrorCode {

  case class Level(key: String)

  object Level {
    val Debug  = new Level("debug")
    val Info   = new Level("info")
    val Warn   = new Level("warn")
    val Error  = new Level("error")
    val Severe = new Level("severe")
  }

  val UserNotFound  = ErrorCode("not.found.user", Level.Info)
  val WrongPassword = ErrorCode("password.wrong", Level.Info)
}
