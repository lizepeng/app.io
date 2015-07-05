package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Person(
  id: UUID,
  first_name: String = "",
  last_name: String = "",
  updated_at: DateTime = DateTime.now
) extends HasUUID with TimeBased

sealed class PersonTable
  extends CassandraTable[PersonTable, Person]
  with CanonicalNamedModel[Person]
  with Logging {

  override val tableName = "persons"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object first_name
    extends StringColumn(this)

  object last_name
    extends StringColumn(this)

  object updated_at
    extends DateTimeColumn(this)

  override def fromRow(r: Row): Person = {
    Person(
      id(r),
      first_name(r),
      last_name(r),
      updated_at(r)
    )
  }
}

object Person
  extends PersonTable
  with ExceptionDefining {

  case class NotFound(id: UUID)
    extends BaseException(error_code("not.found"))

  // Json Reads and Writes
  implicit val json_format = Json.format[Person]
}

class Persons(
  implicit
  val basicPlayApi: BasicPlayApi,
  val cassandraManager: CassandraManager
)
  extends PersonTable
  with EntityTable[Person]
  with ExtCQL[PersonTable, Person]
  with BasicPlayComponents
  with CassandraComponents {

  create.ifNotExists.future()

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw Person.NotFound(id)
    case _    => true
  }

  def find(id: UUID): Future[Person] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None    => throw Person.NotFound(id)
    case Some(u) => u
  }

  def save(p: Person): Future[ResultSet] = CQL {
    update
      .where(_.id eqs p.id)
      .modify(_.first_name setTo p.first_name)
      .and(_.last_name setTo p.last_name)
      .and(_.updated_at setTo DateTime.now)
  }.future()

  def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}