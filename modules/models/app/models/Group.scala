package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra.{Cassandra, ExtCQL}
import models.sys.SysConfig
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.iteratee._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.collection.TraversableOnce
import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class Group(
  id: UUID,
  name: String = "",
  description: Option[String] = None,
  is_internal: Boolean = false,
  updated_at: DateTime
) extends HasUUID {

  def createIfNotExist = Group.createIfNotExist(this)

  def save = Group.save(this)
}

sealed class Groups
  extends CassandraTable[Groups, Group]
  with ExtCQL[Groups, Group]
  with Module[Group]
  with Logging {

  override val tableName = "groups"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]
    with JsonReadable[UUID] {

    def reads = (__ \ "id").read[UUID]
  }

  object name
    extends StringColumn(this)
    with StaticColumn[String]
    with JsonReadable[String] {

    def reads = (__ \ "name").read[String](
      minLength[String](2) keepAnd maxLength[String](255)
    )
  }

  object description
    extends OptionalStringColumn(this)
    with StaticColumn[Option[String]]
    with JsonReadable[Option[String]] {

    def reads = (__ \ "description").readNullable[String]
  }

  object is_internal
    extends OptionalBooleanColumn(this)
    with StaticColumn[Option[Boolean]]
    with JsonReadable[Boolean] {

    def reads = (__ \ "is_internal").read[Boolean]
  }

  object updated_at
    extends DateTimeColumn(this)
    with StaticColumn[DateTime]
    with JsonReadable[DateTime] {

    def reads = always(DateTime.now)
  }

  object child_id
    extends UUIDColumn(this)
    with ClusteringOrder[UUID]

  override def fromRow(r: Row): Group = {
    Group(
      id(r),
      name(r),
      description(r),
      is_internal(r).contains(true),
      updated_at(r)
    )
  }
}

object Group extends Groups with Cassandra with AppConfig {

  case class NotFound(id: UUID)
    extends BaseException(msg_key("not.found"))

  case class NotWritable(id: UUID)
    extends BaseException(msg_key("not.writable"))

  case class NotEmpty(id: UUID)
    extends BaseException(msg_key("not.empty"))

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
  implicit val group_writes = Json.writes[Group]
  implicit val group_reads  = (
    id.reads
      and name.reads
      and description.reads
      and is_internal.reads
      and updated_at.reads
    )(Group.apply _)

  def createIfNotExist(group: Group): Future[Boolean] = CQL {
    insert
      .value(_.id, group.id)
      .value(_.name, group.name)
      .value(_.description, group.description)
      .value(_.is_internal, Some(group.is_internal).filter(_ == true))
      .value(_.updated_at, group.updated_at)
      .ifNotExists()
  }.future().map(_.wasApplied())

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw NotFound(id)
    case _    => true
  }

  def find(id: UUID): Future[Group] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case Some(g) => g
    case None    => throw NotFound(id)
  }

  def find(ids: TraversableOnce[UUID]): Future[Seq[Group]] = {
    stream(ids) |>>> Iteratee.getChunks[Group]
  }

  def stream(ids: TraversableOnce[UUID]): Enumerator[Group] = CQL {
    select(_.id, _.name, _.description, _.is_internal, _.updated_at)
      .distinct
      .where(_.id in ids.toList.distinct)
  }.fetchEnumerator() &>
    Enumeratee.map(t => t.copy(_4 = t._4.contains(true))) &>
    Enumeratee.map(t => Group.apply _ tupled t)

  def save(group: Group): Future[Group] = CQL {
    update
      .where(_.id eqs group.id)
      .modify(_.name setTo group.name)
      .and(_.description setTo group.description)
      .and(_.is_internal setTo Some(group.is_internal).filter(_ == true))
      .and(_.updated_at setTo DateTime.now)
  }.future().map(_ => group)

  def remove(id: UUID): Future[ResultSet] =
    if (InternalGroups.Id2Num.contains(id))
      Future.failed(NotWritable(id))
    else
      CQL {
        select(_.child_id)
          .where(_.id eqs id)
      }.one().recover {
        case e: Exception => None
      }.flatMap {
        case None => CQL {delete.where(_.id eqs id)}.future()
        case _    => throw NotEmpty(id)
      }

  def all: Enumerator[Group] = CQL {
    select(_.id).distinct
  }.fetchEnumerator &>
    Enumeratee.grouped {
      Enumeratee.take(Math.max(fetchSize() / 10, 100)) &>>
        Iteratee.getChunks
    } &> Enumeratee.mapFlatten(stream)

  def children(id: UUID, pager: Pager): Future[Page[UUID]] = {
    CQL {
      select(_.child_id)
        .where(_.id eqs id)
    }.fetchEnumerator() |>>>
      PIteratee.slice[UUID](pager.start, pager.limit)
  }.map(_.toIterable)
    //because child_id could be null
    .recover { case e: Exception => Nil }
    .map(Page(pager, _))

  def addChild(id: UUID, child_id: UUID): Future[ResultSet] = CQL {
    Batch.logged
      .add(Group.cql_updated_at(id))
      .add(User.cql_updated_at(child_id))
      .add(Group.cql_add_child(id, child_id))
      .add(User.cql_add_group(child_id, id))
  }.future()

  def delChild(id: UUID, child_id: UUID): Future[ResultSet] = CQL {
    Batch.logged
      .add(Group.cql_updated_at(id))
      .add(User.cql_updated_at(child_id))
      .add(Group.cql_del_child(id, child_id))
      .add(User.cql_del_group(child_id, id))
  }.future()

  def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)

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

  def cql_updated_at(id: UUID) = CQL {
    update
      .where(_.id eqs id)
      .modify(_.updated_at setTo DateTime.now)
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
  val Div1       = for (gid <- 0 to 6) yield gid
  val Div2       = for (gid <- 7 to 12) yield gid
  val Div3       = for (gid <- 13 to 18) yield gid
  val AnyoneMask = 1 << 18
  val Anyone     = 0

  @volatile private var _num2Id  : Seq[UUID]      = Seq()
  @volatile private var _anyoneId: UUID           = UUIDs.timeBased()
  @volatile private var _id2num  : Map[UUID, Int] = Map()

  def initialize: Future[Boolean] = Future.sequence(
    ALL.map { n =>
      val key = s"internal_group_${"%02d".format(n)}"
      System.UUID(key).flatMap { id =>
        Group(
          id,
          if (n == Anyone) "Anyone" else key,
          Some(key),
          is_internal = true,
          DateTime.now
        ).createIfNotExist.map((id, _))
      }
    }
  ).map { seq =>
    _num2Id = seq.map(_._1)
    _anyoneId = _num2Id(Anyone)
    _id2num = _num2Id.zipWithIndex.toMap
    Logger.info("Internal Group Ids has been initialized.")
    (true /: seq)(_ && _._2)
  }

  def AnyoneId = _anyoneId

  def Id2Num = _id2num

  implicit def toGroupIdSet(igs: InternalGroups): Set[UUID] = {
    igs.numbers.map(_num2Id).toSet
  }
}