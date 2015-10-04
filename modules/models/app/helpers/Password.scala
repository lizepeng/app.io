package helpers

import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{Forms, Mapping}

/**
 * @author zepeng.li@gmail.com
 */
case class Password(self: String) extends AnyVal {

  override def toString = self
}

object Password {

  def empty = Password("")

  implicit def formatter: Formatter[Password] = ExtFormatter.anyValFormatter(Password.apply, _.self)

  def constrained: Mapping[Password] = Forms.of[Password].verifying(
    Constraint[Password]("constraint.email.check") { passwd =>
      passwd.self match {
        case o if isEmpty(o)     => Invalid(ValidationError("password.too.short", 7))
        case o if o.length <= 7  => Invalid(ValidationError("password.too.short", 7))
        case o if o.length >= 39 => Invalid(ValidationError("password.too.long", 39))
        case noDigit()           => Invalid(ValidationError("password.all_number"))
        case noLower()           => Invalid(ValidationError("password.all_upper"))
        case noUpper()           => Invalid(ValidationError("password.all_lower"))
        case _                   => Valid
      }
    }
  )

  private val noDigit = """[^0-9]*""".r
  private val noUpper = """[^A-Z]*""".r
  private val noLower = """[^a-z]*""".r
  private def isEmpty(s: String) = s == null || s.trim.isEmpty
}