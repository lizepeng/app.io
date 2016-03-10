package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.misc._
import models.sys._
import org.joda.time.DateTime
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.collection.TraversableOnce
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
case class Group(
  id: UUID = UUIDs.timeBased,
  name: Name = Name.empty,
  description: Option[String] = None,
  is_internal: Boolean = false,
  updated_at: DateTime = DateTime.now
) extends HasUUID with TimeBased

trait GroupCanonicalNamed extends CanonicalNamed {

  override val basicName = "groups"
}

sealed trait GroupTable
  extends NamedCassandraTable[GroupTable, Group]
  with GroupCanonicalNamed {

  object id
    extends TimeUUIDColumn(this)
    with PartitionKey[UUID]

  object child_id
    extends UUIDColumn(this)
    with ClusteringOrder[UUID]

  object name
    extends StringColumn(this)
    with StaticColumn[String]

  object description
    extends OptionalStringColumn(this)
    with StaticColumn[Option[String]]

  object is_internal
    extends OptionalBooleanColumn(this)
    with StaticColumn[Option[Boolean]]

  object layout
    extends OptionalStringColumn(this)
    with StaticColumn[Option[String]]

  object updated_at
    extends DateTimeColumn(this)
    with StaticColumn[DateTime]

  override def fromRow(r: Row): Group = {
    Group(
      id(r),
      Name(name(r)),
      description(r),
      is_internal(r).contains(true),
      updated_at(r)
    )
  }
}

object Group
  extends GroupCanonicalNamed
  with ExceptionDefining {

  case class NotFound(id: UUID)
    extends BaseException(error_code("not.found"))

  case class NotWritable(id: UUID)
    extends BaseException(error_code("not.writable"))

  case class NotEmpty(id: UUID)
    extends BaseException(error_code("not.empty"))

  implicit val jsonWrites = Json.writes[Group]
}

class Groups(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder,
  val _users: Users,
  val _sysConfig: SysConfigs
)
  extends GroupTable
  with EntityTable[Group]
  with ExtCQL[GroupTable, Group]
  with BasicPlayComponents
  with InternalGroupsComponents
  with CassandraComponents
  with Logging {

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw Group.NotFound(id)
    case _    => true
  }

  def find(id: UUID): Future[Group] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case Some(g) => g
    case None    => throw Group.NotFound(id)
  }

  def find(ids: Traversable[UUID]): Future[Seq[Group]] = {
    (_internalGroups.stream(ids) |>>> Iteratee.getChunks[Group]).map { grps =>
      ids.map {
        id => grps.find(_.id == id)
      }.collect {
        case g if g.nonEmpty => g.get
      }.toSeq
    }
  }

  def save(group: Group): Future[Group] = CQL {
    update
      .where(_.id eqs group.id)
      .modify(_.name setTo group.name.self)
      .and(_.description setTo group.description)
      .and(_.is_internal setTo Some(group.is_internal).filter(_ == true))
      .and(_.updated_at setTo DateTime.now)
  }.future().map(_ => group)

  def remove(id: UUID): Future[ResultSet] =
    if (_internalGroups.InternalGroupIds.contains(id))
      Future.failed(Group.NotWritable(id))
    else
      CQL {
        select(_.child_id)
          .where(_.id eqs id)
      }.one().recover {
        case e: Exception => None
      }.flatMap {
        case None => CQL {delete.where(_.id eqs id)}.future()
        case _    => throw Group.NotEmpty(id)
      }

  def children(id: UUID, pager: Pager): Future[Page[UUID]] = Page(pager) {
    CQL {
      select(_.child_id).where(_.id eqs id)
    }.fetchEnumerator()
  }

  def addChild(id: UUID, child_id: UUID): Future[ResultSet] = CQL {
    Batch.logged
      .add(this.cql_updated_at(id))
      .add(_users.cql_updated_at(child_id))
      .add(this.cql_add_child(id, child_id))
      .add(_users.cql_add_group(child_id, id))
  }.future()

  def delChild(id: UUID, child_id: UUID): Future[ResultSet] = CQL {
    Batch.logged
      .add(this.cql_updated_at(id))
      .add(_users.cql_updated_at(child_id))
      .add(this.cql_del_child(id, child_id))
      .add(_users.cql_del_group(child_id, id))
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

  def cql_updated_at(id: UUID) = CQL {
    update
      .where(_.id eqs id)
      .modify(_.updated_at setTo DateTime.now)
  }

  def all: Enumerator[Group] = CQL {
    select(_.id).distinct
  }.fetchEnumerator &>
    Enumeratee.grouped {
      Enumeratee.take(1000) &>>
        Iteratee.getChunks
    } &> Enumeratee.mapFlatten(_internalGroups.stream)

  def findLayouts(
    ids: TraversableOnce[UUID]
  ): Future[List[(UUID, Option[String])]] = CQL {
    select(_.id, _.layout).where(_.id in ids.toList.distinct)
  }.fetch()

  def setLayout(id: UUID, layout: Layout): Future[Layout] = CQL {
    update
      .where(_.id eqs id)
      .modify(
        _.layout setTo {
          if (layout.layout.isEmpty) None
          else Some(layout.layout)
        }
      )
  }.future().map(_ => layout)

  def isEmpty: Future[Boolean] = _internalGroups.isEmpty

  override def sortable: Set[SortableField] = Set(name)
}

case class InternalGroup(code: Int) extends AnyVal {

  def toBits =
    if (!isValid) InternalGroupBits(0)
    else InternalGroupBits(1 << (InternalGroup.max - code))

  def bit = toBits.bits

  def isValid = code >= InternalGroup.min && code <= InternalGroup.max

  def |(that: InternalGroup) = toBits + that

  override def toString = code.toString
}

object InternalGroup {

  val min = 0
  val max = 18

  val All    = for (code <- min to max) yield InternalGroup(code)
  val Anyone = InternalGroup(0)

  implicit def InternalGroupToInternalGroupBits(ig: InternalGroup): InternalGroupBits = ig.toBits
}

case class InternalGroupBits(bits: Int) extends AnyVal {

  def contains(g: InternalGroup) = g.isValid && exists(g)

  def toInternalGroups = for (g <- InternalGroup.All if exists(g)) yield g

  def |(that: InternalGroupBits) = InternalGroupBits(bits | that.bits)

  def +(g: InternalGroup) = InternalGroupBits(bits | g.bit)

  def -(g: InternalGroup) = InternalGroupBits(bits & ~g.bit)

  private def exists(g: InternalGroup) = {
    val bit = g.bit
    (bits & bit) == bit
  }

  def pprintLine1 = {
    import scala.Predef._
    (for (i <- InternalGroup.All) yield {
      "%3s".format("G" + i.code)
    }).mkString("|   |", "|", "|   |")
  }

  def pprintLine2: String = {
    import scala.Predef._
    "%21s".format((bits << 1).toBinaryString)
      .grouped(1).map {
      case "1" => " Y "
      case _   => "   "
    }.mkString("|", "|", "|")
  }

  override def toString =
    s"""
       |$pprintLine1
       |$pprintLine2
       |""".stripMargin
}

class InternalGroups(
  preLoad: InternalGroups => Future[_],
  postInit: InternalGroups => Future[_]
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder,
  val _sysConfig: SysConfigs
) extends GroupTable
  with EntityTable[Group]
  with ExtCQL[GroupTable, Group]
  with BasicPlayComponents
  with CassandraComponents
  with SysConfig
  with BootingProcess
  with Logging {

  @volatile private var _internalGroupIds: Seq[UUID] = _

  onStart(
    create.ifNotExists.future()
      .andThen { case _ => preLoad(this) }
      .flatMap { case _ => loadOrInit }
      .andThen { case Success(true) => postInit(this) }
  )

  private def loadOrInit: Future[Boolean] = {
    Future.sequence(
      InternalGroup.All.map { n =>
        val key = s"internal_group_${"%02d".format(n.code)}"
        System.UUID(key).flatMap { id =>
          createIfNotExist(
            Group(
              id,
              n match {
                case InternalGroup.Anyone => Name("AnyUsers")
                case _                    => Name(key)
              },
              Some(key),
              is_internal = true,
              DateTime.now
            )
          ).map((id, _))
        }
      }
    ).map { seq =>
      _internalGroupIds = seq.map(_._1)
      val initialized = (true /: seq) (_ && _._2)
      Logger.info(
        if (initialized)
          "Internal Group Ids has been initialized."
        else
          "Internal Group Ids has been loaded."
      )
      initialized
    }
  }

  def AnyoneId = _internalGroupIds(InternalGroup.Anyone.code)

  def InternalGroupIds = _internalGroupIds

  def find(igb: InternalGroupBits): Set[UUID] = {
    igb.toInternalGroups.map(_.code).map(_internalGroupIds).toSet
  }

  private def createIfNotExist(group: Group): Future[Boolean] = CQL {
    insert
      .value(_.id, group.id)
      .value(_.name, group.name.self)
      .value(_.description, group.description)
      .value(_.is_internal, Some(group.is_internal).filter(_ == true))
      .value(_.updated_at, group.updated_at)
      .ifNotExists()
  }.future().map(_.wasApplied())

  def setLayout(id: UUID, layout: Layout): Future[Boolean] = CQL {
    insert
      .value(_.id, id)
      .value(_.layout, Some(layout.layout))
  }.future().map(_.wasApplied)

  def stream(ids: TraversableOnce[UUID]): Enumerator[Group] = CQL {
    select(_.id, _.name, _.description, _.is_internal, _.updated_at)
      .distinct
      .where(_.id in ids.toList.distinct)
  }.fetchEnumerator() &>
    Enumeratee.map(t => t.copy(_2 = Name(t._2), _4 = t._4.contains(true))) &>
    Enumeratee.map(t => Group.apply _ tupled t)

  def all: Enumerator[Group] = CQL {
    select(_.id).distinct.where(_.id in _internalGroupIds.toList)
  }.fetchEnumerator &>
    Enumeratee.grouped {
      Enumeratee.take(1000) &>>
        Iteratee.getChunks
    } &> Enumeratee.mapFlatten(stream)

  def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)

}

trait InternalGroupsComponents {

  def _users: Users

  implicit def _internalGroups: InternalGroups = _users._internalGroups
}

object InternalGroupsComponents {

  implicit def _internalGroups(implicit _users: Users): InternalGroups = _users._internalGroups
}