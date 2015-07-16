package models

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers.ExtCrypto._
import helpers._
import models.cassandra._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class ExpirableLink(
  id: String,
  user_id: UUID,
  module: String
)

trait ExpirableLinkCanonicalNamed extends CanonicalNamed {

  override val basicName = "expirable_links"
}

sealed class ExpirableLinkTable
  extends NamedCassandraTable[ExpirableLinkTable, ExpirableLink]
  with ExpirableLinkCanonicalNamed {

  object id
    extends StringColumn(this)
    with PartitionKey[String]

  object user_id
    extends UUIDColumn(this)

  object module
    extends StringColumn(this)

  override def fromRow(r: Row): ExpirableLink =
    ExpirableLink(id(r), user_id(r), module(r))
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
  val cassandraManager: CassandraManager
)
  extends ExpirableLinkTable
  with ExtCQL[ExpirableLinkTable, ExpirableLink]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  create.ifNotExists.future()

  def find(id: String): Future[ExpirableLink] =
    CQL {
      select.where(_.id eqs id)
    }.one().map {
      case None       => throw ExpirableLink.NotFound(id)
      case Some(link) => link
    }

  def nnew(module: String)(
    implicit user: User
  ): Future[ExpirableLink] = {
    val id = Crypto.sha2(user.salt, 512)
    CQL {
      insert.value(_.id, id)
        .value(_.user_id, user.id)
        .value(_.module, module)
        .ttl(24 * 60 * 60)
    }.future().map {
      _ => ExpirableLink(id, user.id, module)
    }
  }

  def remove(id: String): Future[ResultSet] = CQL {
    delete.where(_.id eqs id)
  }.future()

}