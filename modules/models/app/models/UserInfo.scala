package models

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class UserInfo(
  id: UUID,
  name: String,
  email: String,
  internal_groups: Int
)

object UserInfo {

  //TODO validation
  // Json Reads and Writes
  val reads_id              = (__ \ "id").read[UUID]
  val reads_name            = (__ \ "name").read[String](minLength[String](2) keepAnd maxLength[String](255))
  val reads_email           = (__ \ "email").read[String](email)
  val reads_internal_groups = (__ \ "internal_groups").read[Int]

  implicit val user_writes = Json.writes[UserInfo]
  implicit val user_reads  = (
    reads_id and reads_name and reads_email and reads_internal_groups
    )(UserInfo.apply _)
}
