package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.Implicits._
import helpers._
import models.cassandra._
import models.sys.SysConfig
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class Person(
  id: UUID,
  first_name: String = "",
  last_name: String = ""
) extends TimeBased

sealed class Persons
  extends CassandraTable[Persons, Person]
  with ExtCQL[Persons, Person]
  with Module[Person]
  with Logging {

  override val tableName = "persons"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object first_name extends StringColumn(this)

  object last_name extends StringColumn(this)

  override def fromRow(r: Row): Person = {
    Person(
      id(r),
      first_name(r),
      last_name(r)
    )
  }
}

object Person extends Persons with Cassandra with SysConfig with AppConfig {

  case class NotFound(id: UUID)
    extends BaseException(msg_key("not.found"))

  // Json Reads and Writes
  val reads_id         = (__ \ "id").read[UUID]
  val reads_first_name = (__ \ "first_name").read[String](minLength[String](1) keepAnd maxLength[String](255))
  val reads_last_name  = (__ \ "last_name").read[String](minLength[String](1) keepAnd maxLength[String](255))

  implicit val person_writes = Json.writes[Person]
  implicit val person_reads  = (
    reads_id
      and reads_first_name
      and reads_last_name
    )(Person.apply _)

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw NotFound(id)
    case _    => true
  }

  def find(id: UUID): Future[Person] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None    => throw NotFound(id)
    case Some(u) => u
  }

  def save(p: Person): Future[ResultSet] = CQL {
    update
      .where(_.id eqs p.id)
      .modify(_.first_name setTo p.first_name)
      .and(_.last_name setTo p.last_name)
  }.future()
}