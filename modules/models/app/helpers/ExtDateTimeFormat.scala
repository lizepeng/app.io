package helpers

import play.api.i18n.Messages

/**
 * @author zepeng.li@gmail.com
 */
object ExtDateTimeFormat {

  def DateTimeFormat(key: String, args: Any*)(
    implicit messages: Messages
  ) = org.joda.time.format.DateTimeFormat.forPattern(messages(key, args: _*))
    .withLocale(messages.lang.locale)
}