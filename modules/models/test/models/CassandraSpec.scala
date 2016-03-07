package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import helpers._
import models.cassandra._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

import scala.concurrent._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class CassandraSpec extends Specification with EmbeddedCassandra {

  "In Cassandra, Element which size less than 65535" can {

    "be saved in a List" in {
      val entities = new Entities
      val id = UUIDs.timeBased()
      val value = (0 to 65534).map(_ => 65.toChar).mkString
      val list = (0 to 1).map(_ => value).toList
      val found = for {
        _ <- entities.save(id, list)
        ret <- entities.find(id)
      } yield ret

      val loaded = Await.result(found, 2.seconds)
      loaded.get._2 mustEqual list
    }
  }

  "In Cassandra, Element which size is not less than 65535" can {

    "not be saved in a List" in {
      val entities = new Entities
      val id = UUIDs.timeBased()
      val value = (0 to 65535).map(_ => 65.toChar).mkString
      val list = (0 to 1).map(_ => value).toList
      val found = for {
        _ <- entities.save(id, list)
        ret <- entities.find(id)
      } yield ret

      Await.result(found, 2.seconds) must throwAn[Throwable]
    }
  }
}

import com.websudos.phantom.dsl._

sealed class EntityTable
  extends CassandraTable[EntityTable, (UUID, List[String])] {

  override val tableName = "testing_cassandra_table"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object list
    extends ListColumn[EntityTable, (UUID, List[String]), String](this)

  override def fromRow(r: Row): (UUID, List[String]) = (id(r), list(r))
}

class Entities(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends EntityTable
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess {

  onStart(create.ifNotExists.future())

  def find(id: UUID): Future[Option[(UUID, List[String])]] =
    select.where(_.id eqs id).one

  def save(id: UUID, list: List[String]): Future[ResultSet] =
    update.where(_.id eqs id).modify(_.list setTo list).future()

}