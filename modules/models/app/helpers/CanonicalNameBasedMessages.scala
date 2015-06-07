package helpers

import play.api.i18n.Messages

/**
 * @author zepeng.li@gmail.com
 */
trait CanonicalNameBasedMessages {
  self: CanonicalNamed =>

  def message(key: String, args: Any*)(implicit messages: Messages) = {
    messages(msg_key(key), args: _*)
  }

  def msg_key(key: String) = s"$canonicalName.$key"
}