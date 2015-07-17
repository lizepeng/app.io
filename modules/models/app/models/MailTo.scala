package models

import com.websudos.phantom.dsl._
import helpers.JsonStringifier
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */

trait MailTo extends Any

object MailTo {

  case object Nothing extends MailTo

  case class User(uid: UUID) extends AnyVal with MailTo

  object User {implicit val jsonFormat: Format[User] = Json.format[User]}

  case class Group(gid: UUID) extends AnyVal with MailTo

  object Group {implicit val jsonFormat: Format[Group] = Json.format[Group]}

  implicit val jsonFormat: Format[MailTo] = new Format[MailTo] {

    def reads(json: JsValue): JsResult[MailTo] = {
      JsError("error.expected.mail_to")
        .orElse(Json.fromJson[MailTo.User](json)(MailTo.User.jsonFormat))
        .orElse(Json.fromJson[MailTo.Group](json)(MailTo.Group.jsonFormat))
    }

    def writes(mailTo: MailTo): JsValue = mailTo match {
      case o: MailTo.User  => Json.toJson(o)(MailTo.User.jsonFormat)
      case o: MailTo.Group => Json.toJson(o)(MailTo.Group.jsonFormat)
    }
  }
}

trait MailToJsonStringifier extends JsonStringifier[MailTo] {

  def jsonFormat = MailTo.jsonFormat

  def default = MailTo.Nothing
}