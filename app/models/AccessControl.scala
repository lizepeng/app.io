package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import play.api.Play.current
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
) extends Permission[UUID, String, String] {

  def save = AccessControl.save(this)
}

sealed class AccessControls
  extends CassandraTable[AccessControls, AccessControl]
  with Module[AccessControls, AccessControl]
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

object AccessControl extends AccessControls with Cassandra with AppConfig {

  abstract class Undefined[P](
    action: String,
    resource: String,
    sub_key: String
  ) extends Permission.Undefined[P, String, String](
    s"$fullModuleName.$sub_key"
  )

  abstract class Denied[P](
    action: String,
    resource: String,
    sub_key: String
  ) extends Permission.Denied[P, String, String](
    s"$fullModuleName.$sub_key"
  )

  abstract class Granted[P](
    action: String,
    resource: String,
    sub_key: String
  ) extends Permission.Granted[P, String, String](
    s"$fullModuleName.$sub_key"
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
    if (r.isEmpty)
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

  def save(ac: AccessControl): Future[AccessControl] = CQL {
    update.where(_.principal_id eqs ac.principal)
      .and(_.resource eqs ac.resource)
      .and(_.action eqs ac.action)
      .and(_.is_group eqs ac.is_group)
      .modify(_.granted setTo ac.granted)
  }.future().map { _ => ac }

  def remove(
    principal: UUID,
    resource: String,
    action: String,
    is_group: Boolean
  ): Future[ResultSet] = CQL {
    delete.where(_.principal_id eqs principal)
      .and(_.resource eqs resource)
      .and(_.action eqs action)
      .and(_.is_group eqs is_group)
  }.future()

  def list(pager: Pager): Future[List[AccessControl]] = {
    CQL {
      select.setFetchSize(fetchSize())
    }.fetchEnumerator |>>>
      PIteratee.slice[AccessControl](pager.start, pager.limit)
  }.map(_.toList)
}