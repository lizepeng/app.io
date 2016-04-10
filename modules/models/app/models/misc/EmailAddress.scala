package models.misc

import helpers._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{Forms, Mapping}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.QueryStringBindable.Parsing

/**
 * @author zepeng.li@gmail.com
 */
case class EmailAddress private(chars: IndexedSeq[Char]) {

  val self = String.valueOf(chars.toArray)

  override def toString = self
}

object EmailAddress {

  def apply(self: String): EmailAddress = EmailAddress(self.toLowerCase.toCharArray)

  def empty = EmailAddress("")

  implicit val jsonFormat = Format(
    (minLength[String](5) <~ maxLength[String](40) <~ email).map(js => EmailAddress(js)),
    Writes[EmailAddress](o => JsString(o.self))
  )

  implicit object bindableQueryEmailAddress extends Parsing[EmailAddress](
    EmailAddress(_), _.self, (
      key: String,
      e: Exception
    ) => "Cannot parse parameter %s as EmailAddress: %s".format(key, e.getMessage)
  )

  implicit def formatter: Formatter[EmailAddress] = ExtFormatter.anyValFormatter(EmailAddress.apply, _.self)

  def constrained: Mapping[EmailAddress] = Forms.of[EmailAddress].verifying(
    Constraint[EmailAddress]("constraint.email.check") { email =>
      email.self match {
        case o if isEmpty(o)    => Invalid(ValidationError("email.empty"))
        case o if o.length > 39 => Invalid(ValidationError("email.invalid"))
        case emailRegex()       => Valid
        case _                  => Invalid(ValidationError("email.invalid"))
      }
    }
  )

  private val emailRegex = """[\w\.-]+@[\w\.-]+\.\w+$""".r
  private def isEmpty(s: String) = s == null || s.trim.isEmpty
}