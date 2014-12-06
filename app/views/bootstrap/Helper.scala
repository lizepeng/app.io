package views.html.bootstrap

import views.html.helper._

/**
 * @author zepeng.li@gmail.com
 */
object Helper {

  implicit class RichFieldElements(val fe: FieldElements) extends AnyVal {
    def hasInfos = !fe.infos.isEmpty

    def validationStateClass =
      if (fe.hasErrors) "error"
      else if (hasInfos) "info"
      else ""

    def helpText(implicit lang: play.api.i18n.Lang): Seq[String] = {
      if (fe.hasErrors) fe.errors
      else if (hasInfos) fe.infos
      else Array[String]()
    }
  }

}