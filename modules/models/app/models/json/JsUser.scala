package models.json

import java.util.UUID

import models._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class JsUser(
  id: UUID,
  name: String,
  email: String,
  int_groups: Int,
  ext_groups: Set[UUID]
) {

  def toUser: User = User(
    id = id,
    name = name,
    email = email,
    int_groups = InternalGroups(int_groups),
    ext_groups = ext_groups
  )

}

object JsUser {

  //TODO validation
  // Json Reads and Writes
  val reads_id         = (__ \ "id").read[UUID]
  val reads_name       = (__ \ "name").read[String](minLength[String](2) keepAnd maxLength[String](255))
  val reads_email      = (__ \ "email").read[String](email)
  val reads_int_groups = (__ \ "int_groups").read[Int]
  val reads_ext_groups = (__ \ "ext_groups").read[Set[UUID]]

  implicit val user_writes = Json.writes[JsUser]
  implicit val user_reads  = (
    reads_id
      and reads_name
      and reads_email
      and reads_int_groups
      and reads_ext_groups
    )(JsUser.apply _)

  def from(user: User) = JsUser(
    user.id,
    user.name,
    user.email,
    user.int_groups.code,
    user.ext_groups
  )
}
