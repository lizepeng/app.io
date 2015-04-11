package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import play.api.Play.current

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
case class Group(
  id: UUID,
  name: String,
  description: Option[String]
) {

  def createIfNotExist = Group.createIfNotExist(this)

  def save = Group.save(this)
}

sealed class Groups
  extends CassandraTable[Groups, Group]
  with Module[Groups, Group]
  with ExtCQL[Groups, Group]
  with Logging {

  override val tableName = "groups"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object name
    extends StringColumn(this)
    with StaticColumn[String]

  object description
    extends OptionalStringColumn(this)
    with StaticColumn[Option[String]]

  object child_id
    extends UUIDColumn(this)
    with ClusteringOrder[UUID]

  override def fromRow(r: Row): Group = {
    Group(id(r), name(r), description(r))
  }
}

object Group extends Groups with Cassandra with AppConfig {

  object AccessControl {

    import models.{AccessControl => AC}

    case class Undefined(
      principal: List[UUID],
      action: String,
      resource: String
    ) extends AC.Undefined[List[UUID]](action, resource, moduleName)

    case class Denied(
      principal: List[UUID],
      action: String,
      resource: String
    ) extends AC.Denied[List[UUID]](action, resource, moduleName)

    case class Granted(
      principal: List[UUID],
      action: String,
      resource: String
    ) extends AC.Granted[List[UUID]](action, resource, moduleName)

  }

  def createIfNotExist(group: Group): Future[Group] = CQL {
    insert
      .value(_.id, group.id)
      .value(_.name, group.name)
      .value(_.description, group.description)
      .ifNotExists()
  }.future().map { rs =>
    if (rs.wasApplied()) group else fromRow(rs.one)
  }

  def find[A](ids: List[UUID]): Future[Map[UUID, Group]] = CQL {
    select
      .where(_.id in ids)
  }.fetch().map(_.map(g => (g.id, g)).toMap)

  def save(group: Group): Future[Group] = CQL {
    update
      .where(_.id eqs group.id)
      .modify(_.name setTo group.name)
      .and(_.description setTo group.description)
  }.future().map(_ => group)

  def list(pager: Pager): Future[List[Group]] = {
    CQL {
      select.setFetchSize(fetchSize())
    }.fetchEnumerator |>>>
      PIteratee.slice[Group](pager.start, pager.limit)
  }.map(_.toList)

}

case class InternalGroups(code: Int) {

  def contains(gid: Int) = gid >= 0 && gid <= 18 && exists(gid)

  def numbers = for (gid <- InternalGroups.ALL if exists(gid)) yield gid

  private def exists(gid: Int) = (code & 1 << (19 - 1 - gid)) > 0

  def pprintLine1 = {
    import scala.Predef._
    (for (i <- InternalGroups.ALL) yield {
      "%3s".format("G" + i)
    }).mkString("|   |", "|", "|   |")
  }

  def pprintLine2: String = {
    import scala.Predef._
    "%21s".format((code << 1).toBinaryString)
      .grouped(1).map {
      case "1" => " Y "
      case _   => "   "
    }.mkString("|", "|", "|")
  }

  override def toString =
    s"""
      $pprintLine1
      $pprintLine2
     """
}

object InternalGroups extends helpers.ModuleLike with SysConfig {

  import scala.Predef._
  import scala.language.implicitConversions

  override val moduleName = Group.moduleName

  val ALL     = for (gid <- 0 to 18) yield gid
  val Anyone = 1 << 18

  private lazy val numToId = Await.result(

    Future.sequence(
      ALL.map { n =>
        val key = s"internal_group_${"%02d".format(n)}"
        getUUID(key).andThen {
          case Success(id) =>
            Group(id, key, Some(key)).createIfNotExist
        }
      }
    ), 10 seconds
  )

  implicit def toGroupIdList(igs: InternalGroups): List[UUID] = {
    igs.numbers.map(numToId).toList
  }
}