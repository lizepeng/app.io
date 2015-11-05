package helpers

import play.api.data._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class HumanName(self: String) extends AnyVal with NameLike

object HumanName {

  def empty = HumanName("")

  implicit val jsonFormat = Format(
    (minLength[String](1) <~ maxLength[String](255)).map(js => HumanName(js)),
    Writes[HumanName](o => JsString(o.self))
  )

  implicit def formatter: Formatter[HumanName] = ExtFormatter.anyValFormatter(HumanName.apply, _.self)

  def constrained: Mapping[HumanName] = Forms.of[HumanName].verifying(
    Name.constraint("constraint.human_name.check", minLength = 1)
  )
}

case class Name(self: String) extends AnyVal with NameLike

object Name {

  def empty = Name("")

  implicit val jsonFormat = Format(
    (minLength[String](2) <~ maxLength[String](255)).map(js => Name(js)),
    Writes[Name](o => JsString(o.self))
  )

  implicit def formatter: Formatter[Name] = ExtFormatter.anyValFormatter(Name.apply, _.self)

  def constrained: Mapping[Name] = Forms.of[Name].verifying(constraint())

  def constraint[T <: NameLike](
    name: String = "constraint.name.check",
    minLength: Int = 2,
    maxLength: Int = 255
  ): Constraint[T] = Constraint[T](name) {
    name =>
      name.self match {
        case o if o.length < minLength => Invalid(ValidationError("error.minLength", minLength))
        case o if o.length > maxLength => Invalid(ValidationError("error.maxLength", maxLength))
        case _                         => Valid
      }
  }
}

trait NameLike extends Any {

  def self: String

  override def toString = self
}