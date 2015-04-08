package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.User._
import models.cassandra.{Cassandra, ExtCQL}
import models.sys.SysConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class Group(id: UUID, name: String)

sealed class Groups
  extends CassandraTable[Groups, Group]
  with ExtCQL[Groups, Group]
  with Logging {

  override val tableName = "groups"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object name
    extends StringColumn(this)
    with StaticColumn[String]

  object child_id
    extends UUIDColumn(this)
    with ClusteringOrder[UUID]

  override def fromRow(r: Row): Group = {
    Group(id(r), name(r))
  }
}

object Group extends Groups with Cassandra {

  object AccessControl {

    import models.{AccessControl => AC}

    val key = "groups"

    case class Undefined(
      principal: List[UUID],
      action: String,
      resource: String
    ) extends AC.Undefined[List[UUID]](action, resource, key)

    case class Denied(
      principal: List[UUID],
      action: String,
      resource: String
    ) extends AC.Denied[List[UUID]](action, resource, key)

    case class Granted(
      principal: List[UUID],
      action: String,
      resource: String
    ) extends AC.Granted[List[UUID]](action, resource, key)

  }

  def find[A](ids: List[UUID]): Future[Map[UUID, Group]] = {
    select
      .where(_.id in ids)
      .fetch().map(
        _.map(g => (g.id, g)).toMap
      )
  }

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

object InternalGroups extends Logging with SysConfig {

  import scala.Predef._
  import scala.language.implicitConversions

  override val module_name: String = "models.group"

  val ALL = for (gid <- 0 to 18) yield gid

  private lazy val numToId = Await.result(

    Future.sequence(
      ALL.map(n => getUUID(s"internal_group_${"%02d".format(n)}"))
    ), 10 seconds
  )

  implicit def toGroupIdList(igs: InternalGroups): List[UUID] = {
    igs.numbers.map(numToId).toList
  }
}