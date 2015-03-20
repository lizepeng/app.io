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

  case class Undefined(
    ids: List[UUID],
    action: String,
    resource: String
  ) extends Permission.Undefined("access.control")

  def find(
    resource: String,
    action: String,
    user_id: UUID
  ): Future[Option[Boolean]] = CQL {
    select(_.granted)
      .where(_.principal_id eqs user_id)
      .and(_.resource eqs resource)
      .and(_.action eqs action)
      .and(_.is_group eqs false)
  }.one()

  def find(
    resource: String,
    action: String,
    group_ids: List[UUID]
  ): Future[Option[Boolean]] = CQL {
    select(_.granted)
      .where(_.principal_id in group_ids)
      .and(_.resource eqs resource)
      .and(_.action eqs action)
      .and(_.is_group eqs true)
  }.fetch().map {r =>
    if (r.isEmpty || r.size != group_ids.size) None
    else Some(!r.contains(false))
  }

  def find(
    resource: String,
    action: String,
    group_ids: Future[List[UUID]]
  ): Future[Option[Boolean]] =
    for {
      ids <- group_ids
      ret <- find(resource, action, ids)
    } yield ret
}