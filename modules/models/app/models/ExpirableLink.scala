package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers.ExtCrypto._
import helpers._
import models.cassandra._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class ExpirableLink(id: String, target_id: String)

trait ExpirableLinkCanonicalNamed extends CanonicalNamed {

  override val basicName = "expirable_links"
}

sealed class ExpirableLinkTable
  extends NamedCassandraTable[ExpirableLinkTable, ExpirableLink]
  with ExpirableLinkCanonicalNamed {

  object id
    extends StringColumn(this)
    with PartitionKey[String]

  object target_id
    extends StringColumn(this)

  override def fromRow(r: Row): ExpirableLink =
    ExpirableLink(id(r), target_id(r))
}

object ExpirableLink
  extends ExpirableLinkCanonicalNamed
  with ExceptionDefining {

  case class NotFound(id: String)
    extends BaseException(error_code("not.found"))

}

class ExpirableLinks(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends ExpirableLinkTable
  with ExtCQL[ExpirableLinkTable, ExpirableLink]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def find(id: String): Future[ExpirableLink] =
    CQL {
      select.where(_.id eqs id)
    }.one().map {
      case None       => throw ExpirableLink.NotFound(id)
      case Some(link) => link
    }

  def save(
    target_id: String,
    ttl: FiniteDuration = 24 hours
  ): Future[ExpirableLink] = {
    val id = Crypto.sha2(s"$target_id--${UUID.randomUUID()}", 512)
    CQL {
      insert.value(_.id, id)
        .value(_.target_id, target_id)
        .ttl(ttl.toSeconds)
    }.future().map {
      _ => ExpirableLink(id, target_id)
    }
  }

  def remove(id: String): Future[ResultSet] =
    CQL {
      delete.where(_.id eqs id)
    }.future()
}