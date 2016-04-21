package models.misc

import helpers.Stringifier
import play.api.libs.json.Json

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
case class Layout(layout: String) extends AnyVal

object Layout {

  implicit val jsonFormat = Json.format[Layout]

  implicit val stringifier = new Stringifier[Layout] {

    def << = str => Success(Layout(str))

    def >>: = _.layout
  }
}