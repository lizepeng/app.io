package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class AccessControlEntry(
  resource: String,
  permission: Long,
  principal_id: UUID,
  is_group: Boolean
)
  extends HasID[String] {

  override def id = AccessControlEntry.genId(resource, principal_id)

  def save(implicit repo: AccessControls) = repo.save(this)
}

trait AccessControlCanonicalNamed extends CanonicalNamed {

  override val basicName = "access_controls"
}

sealed class AccessControlTable
  extends NamedCassandraTable[AccessControlTable, AccessControlEntry]
  with AccessControlCanonicalNamed {

  object principal_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object resource
    extends StringColumn(this)
    with PrimaryKey[String]

  object is_group
    extends BooleanColumn(this)
    with PrimaryKey[Boolean]

  object permission
    extends LongColumn(this)

  override def fromRow(r: Row): AccessControlEntry = {
    AccessControlEntry(
      resource(r),
      permission(r),
      principal_id(r),
      is_group(r)
    )
  }
}

object AccessControlEntry
  extends AccessControlCanonicalNamed
  with ExceptionDefining {

  case class NotFound(id: UUID, resource: String)
    extends BaseException(error_code("not.found"))

  implicit val jsonFormat = Format[AccessControlEntry](
    Json.reads[AccessControlEntry],
    new Writes[AccessControlEntry] {
      override def writes(o: AccessControlEntry): JsValue =
        Json.obj(
          "resource" -> o.resource,
          "permission" ->f"${o.permission.toBinaryString}%64s".replace(' ', '0'),
          "principal_id" -> o.principal_id,
          "is_group" -> o.is_group
          )
    }
  )

  def genId(
    resource: String,
    principal: UUID
  ) = s"${principal.toString}/$resource"

}

class AccessControls(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends AccessControlTable
  with EntityTable[AccessControlEntry]
  with ExtCQL[AccessControlTable, AccessControlEntry]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def find(ace: AccessControlEntry): Future[AccessControlEntry] = find(ace.principal_id, ace.resource)

  def find(
    principal_id: UUID,
    resource: String
  ): Future[AccessControlEntry] = {
    CQL {
      select
        .where(_.principal_id eqs principal_id)
        .and(_.resource eqs resource)
    }.one().map {
      case Some(ace) => ace
      case None      => throw AccessControlEntry.NotFound(principal_id, resource)
    }
  }

  def findPermission(resource: String, uid: UUID): Future[Option[Long]] = {
    CQL {
      select(_.permission)
        .where(_.principal_id eqs uid)
        .and(_.resource eqs resource)
        .and(_.is_group eqs false)
    }.one()
  }

  def findPermissions(resource: String, gids: Set[UUID]): Future[List[Long]] = {
    CQL {
      select(_.permission)
        .where(_.principal_id in gids.toList)
        .and(_.resource eqs resource)
        .and(_.is_group eqs true)
    }.fetch()
  }

  def save(ace: AccessControlEntry): Future[AccessControlEntry] =
    CQL {
      update.where(_.principal_id eqs ace.principal_id)
        .and(_.resource eqs ace.resource)
        .and(_.is_group eqs ace.is_group)
        .modify(_.permission setTo ace.permission)
    }.future().map { _ => ace }

  def remove(
    principal: UUID,
    resource: String
  ): Future[ResultSet] =
    CQL {
      delete.where(_.principal_id eqs principal)
        .and(_.resource eqs resource)
    }.future()

  def all: Enumerator[AccessControlEntry] =
    CQL(select).fetchEnumerator

  def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)

  override def sortable: Set[SortableField] = Set(principal_id, resource)
}

trait AccessControl[P, A, R] {

  def canAccess: Boolean

  def canAccessAsync: Future[Boolean] = Future.successful(canAccess)

  def principal: P

  def access: A

  def resource: R
}

object AccessControl extends AccessControlCanonicalNamed {

  abstract class Denied[P, A, R](prefix: String)
    extends BaseException(s"$prefix.permission.denied")
    with AccessControl[P, A, R] {

    final override def canAccess = false
  }

}