package models.json

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
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

  def toUser(implicit _internalGroups: InternalGroups): User = User(
    id = id,
    name = name,
    email = email,
    internal_groups_code = InternalGroupsCode(int_groups),
    external_groups = ext_groups
  )(_internalGroups)

}

object JsUser {

  implicit val user_writes = Json.writes[JsUser]
  implicit val user_reads  = (
    User.id.always(UUIDs.timeBased)
      and User.name.reads
      and User.email.reads
      and User.internal_groups.always(0)
      and User.external_groups.always(Set())
    )(JsUser.apply _)

  def from(user: User) = JsUser(
    user.id,
    user.name,
    user.email,
    user.internal_groups_code.code,
    user.external_groups
  )
}
