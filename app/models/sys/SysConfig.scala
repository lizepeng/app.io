package models.sys

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import helpers.Logging
import models.cassandra.{Cassandra, ExtCQL}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class SysConfigEntry(
  module: String,
  key: String,
  value: String
)

sealed class SysConfigs
  extends CassandraTable[SysConfigs, SysConfigEntry]
  with ExtCQL[SysConfigs, SysConfigEntry]
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

object SysConfig extends SysConfigs with Logging with Cassandra {

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
        CQL {
          update
            .where(_.module eqs module)
            .and(_.key eqs key)
            .modify(_.value setTo (op >>: serializer))
        }.future().map(_ => op)
      case Some(value) =>
        Future.successful(serializer << value)
    }
  }

  trait Serializer[T] {
    def << : String => T

    def >>: : T => String
  }

  implicit val uuidSerializer = new Serializer[UUID] {
    def << = UUID.fromString

    def >>: = _.toString
  }

}

trait SysConfig {
  lazy val config_key = this.getClass.getCanonicalName

  def getUUID(key: String) = {
    SysConfig.getOrElseUpdate(config_key, key, UUIDs.timeBased())
  }
}