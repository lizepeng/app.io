package models.misc

import helpers._
import play.api.data._
import play.api.data.format.Formatter
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class PersonalName(
  last: PersonalName.Part = PersonalName.Part.empty,
  first: PersonalName.Part = PersonalName.Part.empty,
  middle: Seq[PersonalName.Part] = Seq()
)

object PersonalName extends PersonalNameJsonStringifier {

  case class Part(self: String) extends AnyVal with NameLike

  object Part {

    def empty = Part("")

    implicit val jsonFormat = Format(
      (minLength[String](1) <~ maxLength[String](255)).map(js => Part(js)),
      Writes[Part](o => JsString(o.self))
    )

    implicit def formatter: Formatter[Part] = ExtFormatter.anyValFormatter(Part.apply, _.self)

    def constrained: Mapping[Part] = Forms.of[Part].verifying(
      NameLike.constraint("constraint.personal_name.part.check", minLength = 1)
    )
  }
}

trait PersonalNameJsonStringifier extends JsonStringifier[PersonalName] {

  implicit val jsonFormat = Json.format[PersonalName]

  def default = PersonalName()
}