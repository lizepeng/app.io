package views.html

import play.twirl.api.Html
import views.html.helper.{FieldConstructor, FieldElements}

/**
 * @author zepeng.li@gmail.com
 */
case class ChainedFieldConstructor(fc: FieldConstructor, others: FieldConstructor*) extends FieldConstructor {

  def apply(elts: FieldElements): Html = {
    (fc(elts) /: others)((html, fc) => fc(elts.copy(input = html)))
  }
}