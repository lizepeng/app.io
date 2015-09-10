package helpers

import play.api.libs.json.Json

/**
 * @author zepeng.li@gmail.com
 */
case class Layout(layout: String) extends AnyVal

object Layout {

  implicit val jsonFormat = Json.format[Layout]

  implicit val layoutSerializer = new Stringifier[Layout] {

    def << = Layout.apply

    def >>: = _.layout
  }
}