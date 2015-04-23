package controllers.api

import helpers.ModuleLike
import play.api.i18n._

/**
 * @author zepeng.li@gmail.com
 */
abstract class MVModule(override val moduleName: String) extends ModuleLike {

  def vmsg(key: String, args: Any*)(implicit lang: Lang) = {
    Messages(s"views.$moduleName.$key", args: _*)
  }
}