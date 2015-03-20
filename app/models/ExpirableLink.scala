package models

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.{Logging, _}
import models.cassandra.{Cassandra, ExtCQL}
import play.api.libs.Crypto

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class ExpirableLink(
  id: String,
  user_id: UUID,
  module: String
)

sealed class ExpirableLinks
  extends CassandraTable[ExpirableLinks, ExpirableLink]
  with ExtCQL[ExpirableLinks, ExpirableLink]
  with Logging {

  override val tableName = "expirable_links"

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

object ExpirableLink extends ExpirableLinks with Cassandra {

  case class NotFound(id: String)
    extends BaseException("not.found.expirable.link")

  def find(id: String): Future[ExpirableLink] =
    CQL {
      select.where(_.id eqs id)
    }.one().map {
      case None => throw NotFound(id)
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