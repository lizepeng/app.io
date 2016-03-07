package models.cfs

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import play.api.libs.iteratee.Enumerator

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait RootCanonicalNamed extends CanonicalNamed {

  override val basicName = "file_system_root"
}

sealed class RootTable
  extends NamedCassandraTable[RootTable, UUID]
  with RootCanonicalNamed {

  object inode_id
    extends TimeUUIDColumn(this)
    with PartitionKey[UUID]

  override def fromRow(r: Row): UUID = inode_id(r)
}

class Root(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends RootTable
  with ExtCQL[RootTable, UUID]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def add(inode_id: UUID): Future[Boolean] = CQL {
    insert
      .value(_.inode_id, inode_id)
      .ifNotExists()
  }.future().map(_.wasApplied())

  def stream: Enumerator[UUID] = CQL(select).fetchEnumerator()
}