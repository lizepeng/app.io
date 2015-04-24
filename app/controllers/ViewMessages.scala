package controllers

import helpers.ModuleLike
import play.api.i18n._

/**
 * @author zepeng.li@gmail.com
 */
trait ViewMessages {
  self: ModuleLike =>

  def vmsg(key: String, args: Any*)(implicit lang: Lang) = {
    Messages(s"views.$moduleName.$key", args: _*)
  }
}