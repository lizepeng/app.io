package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.collection.TraversableOnce
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
  with ExtCQL[AccessControls, AccessControl]
  with Module[AccessControl]
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

  case class NotFound(id: UUID, res: String, act: String)
    extends BaseException(msg_key("not.found"))

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

  // Json Reads and Writes
  val reads_principal = (__ \ "principal").read[UUID]
  val reads_resource  = (__ \ "resource").read[String]
  val reads_action    = (__ \ "action").read[String]
  val reads_is_group  = (__ \ "is_group").read[Boolean]
  val reads_granted   = (__ \ "granted").read[Boolean]

  implicit val access_control_writes = Json.writes[AccessControl]
  implicit val access_control_reads  = (
    reads_resource
      and reads_action
      and reads_principal
      and reads_is_group
      and reads_granted
    )(AccessControl.apply _)

  def find(id: UUID): Future[Seq[AccessControl]] = CQL {
    select.where(_.principal_id eqs id)
  }.fetch()

  def find(ac: AccessControl): Future[AccessControl] =
    find(ac.principal, ac.resource, ac.action)

  def find(
    id: UUID,
    res: String,
    act: String
  ): Future[AccessControl] = CQL {
    select
      .where(_.principal_id eqs id)
      .and(_.resource eqs res)
      .and(_.action eqs act)
  }.one().map {
    case Some(ac) => ac
    case None     => throw NotFound(id, res, act)
  }

  def check(
    res: String,
    act: String,
    user_id: UUID
  ): Future[Granted[UUID]] = CQL {
    select(_.granted)
      .where(_.principal_id eqs user_id)
      .and(_.resource eqs res)
      .and(_.action eqs act)
      .and(_.is_group eqs false)
  }.one().map { r =>
    import User.AccessControl._
    r match {
      case None        =>
        throw Undefined(user_id, act, res)
      case Some(false) =>
        throw Denied(user_id, act, res)
      case Some(true)  =>
        Granted(user_id, act, res)
    }
  }

  def check(
    res: String,
    act: String,
    group_ids: TraversableOnce[UUID]
  ): Future[Granted[Set[UUID]]] = {
    val gids = group_ids.toSet
    CQL {
      select(_.granted)
        .where(_.principal_id in gids.toList)
        .and(_.resource eqs res)
        .and(_.action eqs act)
        .and(_.is_group eqs true)
    }.fetch().map { r =>
      import Group.AccessControl._
      if (r.isEmpty)
        throw Undefined(gids, act, res)
      else if (r.contains(false))
        throw Denied(gids, act, res)
      else Granted(gids, act, res)
    }
  }

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

  def all: Enumerator[AccessControl] = {
    CQL {
      select.setFetchSize(fetchSize())
    }.fetchEnumerator
  }
}