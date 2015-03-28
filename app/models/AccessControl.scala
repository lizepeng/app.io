package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.cassandra.{Cassandra, ExtCQL}
import security.Permission

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class AccessControl(
  resource: String,
  action: String,
  principal: UUID,
  is_group: Boolean,
  granted: Boolean
) extends Permission[UUID, String, String]

sealed class AccessControls
  extends CassandraTable[AccessControls, AccessControl]
  with ExtCQL[AccessControls, AccessControl]
  with Logging {

  override val tableName = "access_controls"

  object principal_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object resource
    extends StringColumn(this)
    with PrimaryKey[String]

  object action
    extends StringColumn(this)
    with PrimaryKey[String]

  object is_group
    extends BooleanColumn(this)
    with PrimaryKey[Boolean]

  object granted
    extends BooleanColumn(this)

  override def fromRow(r: Row): AccessControl = {
    AccessControl(
      resource(r),
      action(r),
      principal_id(r),
      is_group(r),
      granted(r)
    )
  }
}

object AccessControl extends AccessControls with Cassandra {

  abstract class Undefined[P](
    action: String,
    resource: String,
    module_name: String
  ) extends Permission.Undefined[P, String, String](
    s"ac.$module_name"
  )

  abstract class Denied[P](
    action: String,
    resource: String,
    module_name: String
  ) extends Permission.Denied[P, String, String](
    s"ac.$module_name"
  )

  abstract class Granted[P](
    action: String,
    resource: String,
    module_name: String
  ) extends Permission.Granted[P, String, String](
    s"ac.$module_name"
  )

  def find(
    resource: String,
    action: String,
    user_id: UUID
  ): Future[Granted[UUID]] = CQL {
    select(_.granted)
      .where(_.principal_id eqs user_id)
      .and(_.resource eqs resource)
      .and(_.action eqs action)
      .and(_.is_group eqs false)
  }.one().map { r =>
    import User.AccessControl._
    r match {
      case None        =>
        throw Undefined(user_id, action, resource)
      case Some(false) =>
        throw Denied(user_id, action, resource)
      case Some(true)  =>
        Granted(user_id, action, resource)
    }
  }

  def find(
    resource: String,
    action: String,
    group_ids: List[UUID]
  ): Future[Granted[List[UUID]]] = CQL {
    select(_.granted)
      .where(_.principal_id in group_ids)
      .and(_.resource eqs resource)
      .and(_.action eqs action)
      .and(_.is_group eqs true)
  }.fetch().map { r =>
    import Group.AccessControl._
    if (r.isEmpty || r.size != group_ids.size)
      throw Undefined(group_ids, action, resource)
    else if (r.contains(false))
      throw Denied(group_ids, action, resource)
    else Granted(group_ids, action, resource)
  }

  def find(
    resource: String,
    action: String,
    group_ids: Future[List[UUID]]
  ): Future[Granted[List[UUID]]] =
    for {
      ids <- group_ids
      ret <- find(resource, action, ids)
    } yield ret
}