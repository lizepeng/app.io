package models.sys

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra.{Cassandra, ExtCQL}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait SysConfig {
  self: CanonicalNamed =>

  import SysConfig._

  object System {

    def config[T](key: String, default: T)(
      implicit serializer: Serializer[T],
      sysConfig: SysConfigs
    ) = {
      sysConfig.getOrElseUpdate(
        canonicalName, key, default
      )(serializer)
    }

    def UUID(key: String)(
      implicit sysConfig: SysConfigs
    ) = {
      sysConfig.getOrElseUpdate(
        canonicalName, key, UUIDs.timeBased()
      )(uuidSerializer)
    }
  }

}

case class SysConfigEntry(
  module: String,
  key: String,
  value: String
)

sealed class SysConfigTable
  extends CassandraTable[SysConfigTable, SysConfigEntry]
  with Logging {

  override val tableName = "system_config"

  object module
    extends StringColumn(this)
    with PartitionKey[String]

  object key
    extends StringColumn(this)
    with ClusteringOrder[String] with Ascending

  object value extends StringColumn(this)

  override def fromRow(r: Row): SysConfigEntry = {
    SysConfigEntry(module(r), key(r), value(r))
  }
}

object SysConfig extends SysConfigTable {

  trait Serializer[T] {

    def << : String => T

    def >>: : T => String
  }

  implicit val uuidSerializer = new Serializer[UUID] {
    def << = UUID.fromString

    def >>: = _.toString
  }

  implicit val stringSerializer = new Serializer[String] {
    def << = s => s

    def >>: = s => s
  }
}

class SysConfigs(
  implicit val basicPlayApi: BasicPlayApi
)
  extends SysConfigTable
  with ExtCQL[SysConfigTable, SysConfigEntry]
  with BasicPlayComponents
  with Cassandra {

  import SysConfig._

  def getOrElseUpdate[T](
    module: String,
    key: String,
    op: => T
  )(implicit serializer: Serializer[T]): Future[T] = {
    CQL {
      select(_.value)
        .where(_.module eqs module)
        .and(_.key eqs key)
    }.one().flatMap {
      case None        =>
        val value: T = op
        CQL {
          update
            .where(_.module eqs module)
            .and(_.key eqs key)
            .modify(_.value setTo (value >>: serializer))
        }.future().map(_ => value)
      case Some(value) =>
        Future.successful(serializer << value)
    }
  }

}