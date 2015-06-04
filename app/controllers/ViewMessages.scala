package controllers

import helpers.CanonicalNamed
import play.api.i18n._

/**
 * @author zepeng.li@gmail.com
 */
trait ViewMessages {
  self: CanonicalNamed =>

  def vmsg(key: String, args: Any*)(implicit messages: Messages) = {
    messages(s"views.$basicName.$key", args: _*)
  }
}