package models.sys

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra.{CassandraComponents, ExtCQL}

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
      sysConfig.getOrElseInsert(
        canonicalName, key, default
      )(serializer)
    }

    def UUID(key: String)(
      implicit sysConfig: SysConfigs
    ) = {
      sysConfig.getOrElseInsert(
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
  extends CassandraTable[SysConfigTable, SysConfigEntry] {

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

object SysConfig {

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
  implicit
  val basicPlayApi: BasicPlayApi,
  val cassandraManager: CassandraManager
)
  extends SysConfigTable
  with ExtCQL[SysConfigTable, SysConfigEntry]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  create.ifNotExists.future()

  import SysConfig._

  def getOrElseInsert[T](
    module: String,
    key: String,
    op: => T
  )(implicit serializer: Serializer[T]): Future[T] = {
    for {
      maybe <- CQL {
        select(_.value)
          .where(_.module eqs module)
          .and(_.key eqs key)
      }.one()
      value <- maybe match {
        case None    =>
          val v: T = op
          CQL {
            insert
              .value(_.module, module)
              .value(_.key, key)
              .value(_.value, v >>: serializer)
              .ifNotExists()
          }.future().flatMap { rs =>
            if (rs.wasApplied()) Future.successful(v)
            else getOrElseInsert(module, key, op)
          }
        case Some(v) =>
          Future.successful(serializer << v)
      }
    } yield value
  }
}