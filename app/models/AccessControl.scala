package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.cassandra.{Cassandra, ExtCQL}
import models.security.Permission

/**
 * @author zepeng.li@gmail.com
 */
case class AccessControl(
  principal: UUID,
  action: String,
  resource: String
) extends Permission[UUID, String, String]

sealed class AccessControls
  extends CassandraTable[AccessControls, AccessControl]
  with ExtCQL[AccessControls, AccessControl]
  with Logging {

  override val tableName = "access_controls"

  object resource
    extends StringColumn(this)
    with PartitionKey[String]

  object action
    extends StringColumn(this)
    with PartitionKey[String]

  object principal_id
    extends UUIDColumn(this)
    with ClusteringOrder[UUID]

  object is_group
    extends BooleanColumn(this)

  object granted
    extends BooleanColumn(this)

  override def fromRow(r: Row): AccessControl = {
    AccessControl(principal_id(r), action(r), resource(r))
  }
}

object AccessControls extends AccessControls with Cassandra {

}