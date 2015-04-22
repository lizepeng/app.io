package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.batch.BatchStatement
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import models.sys.SysConfig
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.collection.TraversableOnce
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
  description: Option[String],
  is_internal: Boolean
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

  object is_internal
    extends OptionalBooleanColumn(this)
    with StaticColumn[Option[Boolean]]

  object child_id
    extends UUIDColumn(this)
    with ClusteringOrder[UUID]

  override def fromRow(r: Row): Group = {
    Group(id(r), name(r), description(r), is_internal(r).contains(true))
  }
}

object Group extends Groups with Cassandra with AppConfig {

  case class NotFound(id: UUID)
    extends BaseException(msg_key("not.found"))

  case class NotWritable(id: UUID)
    extends BaseException(msg_key("not.writable"))

  object AccessControl {

    import models.{AccessControl => AC}

    case class Undefined(
      principal: Set[UUID],
      action: String,
      resource: String
    ) extends AC.Undefined[Set[UUID]](action, resource, moduleName)

    case class Denied(
      principal: Set[UUID],
      action: String,
      resource: String
    ) extends AC.Denied[Set[UUID]](action, resource, moduleName)

    case class Granted(
      principal: Set[UUID],
      action: String,
      resource: String
    ) extends AC.Granted[Set[UUID]](action, resource, moduleName)

  }

  // Json Reads and Writes
  val reads_name        = (__ \ "name").read[String](minLength[String](2) keepAnd maxLength[String](255))
  val reads_id          = (__ \ "id").read[UUID]
  val reads_description = (__ \ "description").readNullable[String]
  val reads_is_internal = (__ \ "is_internal").read[Boolean]

  implicit val group_writes = Json.writes[Group]
  implicit val group_reads  = (
    reads_id and reads_name and reads_description and reads_is_internal
    )(Group.apply _)

  def createIfNotExist(group: Group): Future[Group] = CQL {
    insert
      .value(_.id, group.id)
      .value(_.name, group.name)
      .value(_.description, group.description)
      .value(_.is_internal, Some(group.is_internal).filter(_ == true))
      .ifNotExists()
  }.future().map { rs =>
    if (rs.wasApplied()) group else fromRow(rs.one)
  }

  def find(id: UUID): Future[Group] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case Some(g) => g
    case None    => throw NotFound(id)
  }

  def find(ids: TraversableOnce[UUID]): Future[Map[UUID, Group]] = CQL {
    select
      .where(_.id in ids.toSet.toList)
  }.fetch().map(_.map(g => (g.id, g)).toMap)

  def save(group: Group): Future[Group] = CQL {
    update
      .where(_.id eqs group.id)
      .modify(_.name setTo group.name)
      .and(_.description setTo group.description)
      .and(_.is_internal setTo Some(group.is_internal).filter(_ == true))
  }.future().map(_ => group)

  def remove(id: UUID): Future[ResultSet] =
    if (InternalGroups.Id2Num.contains(id))
      Future.failed(NotWritable(id))
    else
      CQL {delete.where(_.id eqs id)}.future()

  def list(pager: Pager): Future[Page[Group]] = {
    CQL {
      distinct(_.id).setFetchSize(fetchSize())
    }.fetchEnumerator |>>>
      PIteratee.slice[UUID](pager.start, pager.limit)
  }.flatMap(find).map(_.values).map(Page(pager, _))

  def children(id: UUID, pager: Pager): Future[Page[UUID]] = {
    CQL {
      select(_.child_id)
        .where(_.id eqs id)
        .setFetchSize(fetchSize())
    }.fetchEnumerator() |>>>
      PIteratee.slice[UUID](pager.start, pager.limit)
  }.map(_.toIterable)
    //because child_id could be null
    .recover { case e: Exception => Nil }
    .map(Page(pager, _))

  def addChild(id: UUID, child_id: UUID): Future[ResultSet] = CQL {
    BatchStatement()
      .add(Group.cql_add_child(id, child_id))
      .add(User.cql_add_group(child_id, id))
  }.future()

  def delChild(id: UUID, child_id: UUID): Future[ResultSet] = CQL {
    BatchStatement()
      .add(Group.cql_del_child(id, child_id))
      .add(User.cql_del_group(child_id, id))
  }.future()

  def cql_add_child(id: UUID, child_id: UUID) = CQL {
    insert
      .value(_.id, id)
      .value(_.child_id, child_id)
  }

  def cql_del_child(id: UUID, child_id: UUID) = CQL {
    delete
      .where(_.id eqs id)
      .and(_.child_id eqs child_id)
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

object InternalGroups extends helpers.ModuleLike with SysConfig {

  import scala.Predef._
  import scala.language.implicitConversions

  override val moduleName = Group.moduleName

  val ALL        = for (gid <- 0 to 18) yield gid
  val Half1st    = for (gid <- 0 to 9) yield gid
  val Half2nd    = for (gid <- 10 to 18) yield gid
  val AnyoneMask = 1 << 18
  val Anyone     = 0
  val AnyoneId   = Num2Id(Anyone)

  private lazy val Num2Id = Await.result(

    Future.sequence(
      ALL.map { n =>
        val key = s"internal_group_${"%02d".format(n)}"
        getUUID(key).andThen {
          case Success(id) =>
            Group(id, key, Some(key), is_internal = true).createIfNotExist
        }
      }
    ), 10 seconds
  )

  lazy val Id2Num = Num2Id.zipWithIndex.toMap

  implicit def toGroupIdSet(igs: InternalGroups): Set[UUID] = {
    igs.numbers.map(Num2Id).toSet
  }
}