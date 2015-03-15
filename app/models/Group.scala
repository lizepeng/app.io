package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.cassandra.{Cassandra, ExtCQL}

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
}

case class InternalGroups(code: Int) {

  def all = for (gid <- InternalGroups.all if contains(gid)) yield gid

  def contains(gid: Int) =
    gid >= 0 && gid <= 18 &&
      ((code & 1 << (19 - 1 - gid)) > 0)

  def pprintLine1 = {
    import scala.Predef._
    (for (i <- InternalGroups.all) yield {
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

object InternalGroups {
  val all = for (gid <- 0 to 18) yield gid
}