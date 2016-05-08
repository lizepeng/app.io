package models.misc

import helpers._
import play.api.data._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class Name(self: String) extends AnyVal with NameLike

object Name extends NameJsonStringifier {

  implicit def formatter: Formatter[Name] = ExtFormatter.anyValFormatter(Name.apply, _.self)

  def constrained: Mapping[Name] = Forms.of[Name].verifying(NameLike.constraint())
}

trait NameJsonStringifier extends JsonStringifier[Name] {

  implicit val jsonFormat = Format(
    (minLength[String](2) <~ maxLength[String](255)).map(js => Name(js)),
    Writes[Name](o => JsString(o.self))
  )

  def default = Name("")
}

/**
 * @author zepeng.li@gmail.com
 */
case class UserName private(chars: Array[Char]) extends NameLike {

  val self = String.valueOf(chars)

  override def toString = self
}

object UserName extends UserNameJsonStringifier {

  def apply(self: String): UserName = UserName(self.toLowerCase.toCharArray)

  implicit def formatter: Formatter[UserName] = ExtFormatter.anyValFormatter(UserName.apply, _.self)

  def constrained: Mapping[UserName] = Forms.of[UserName].verifying(NameLike.constraint(minLength = 4))
}

trait UserNameJsonStringifier extends JsonStringifier[UserName] {

  implicit val jsonFormat = Format(
    (minLength[String](4) <~ maxLength[String](255)).map(js => UserName(js)),
    Writes[UserName](o => JsString(o.self))
  )

  def default = UserName("")
}

trait NameLike extends Any {

  def self: String

  override def toString = self
}

object NameLike {

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