package helpers

import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class FirstName(self: String) extends AnyVal

object FirstName {

  implicit val jsonFormat = Format(
    minLength[String](1).map(js => FirstName(js)),
    Writes[FirstName](o => JsString(o.self))
  )
}

case class LastName(self: String) extends AnyVal

object LastName {

  implicit val jsonFormat = Format(
    minLength[String](1).map(js => LastName(js)),
    Writes[LastName](o => JsString(o.self))
  )
}

case class Name(self: String) extends AnyVal

object Name {

  implicit val jsonFormat = Format(
    minLength[String](2).map(js => Name(js)),
    Writes[Name](o => JsString(o.self))
  )
}