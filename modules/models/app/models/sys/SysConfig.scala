package models.sys

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait SysConfig {
  self: CanonicalNamed =>

  object System {

    def config[T](key: String, default: T)(
      implicit serializer: Stringifier[T], sysConfig: SysConfigs
    ) = {
      sysConfig.getOrElseUpdate(
        canonicalName, key, default
      )(serializer)
    }

    def UUID(key: String)(
      implicit _sysConfig: SysConfigs
    ) = {
      _sysConfig.getOrElseUpdate(
        canonicalName, key, UUIDs.timeBased()
      )
    }
  }

}

case class SysConfigEntry(
  module: String,
  key: String,
  value: String
)

sealed class SysConfigTable
  extends NamedCassandraTable[SysConfigTable, SysConfigEntry]
  with SysConfigCanonicalNamed {

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

trait SysConfigCanonicalNamed extends CanonicalNamed {

  override val basicName = "system_config"
}

object SysConfig
  extends SysConfigCanonicalNamed
  with ExceptionDefining {

  case class NotFound(module: String, key: String)
    extends BaseException(error_code("not.found"))

}

class SysConfigs(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends SysConfigTable
  with ExtCQL[SysConfigTable, SysConfigEntry]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def find(module: String, key: String): Future[SysConfigEntry] = CQL {
    select.where(_.module eqs module).and(_.key eqs key)
  }.one().map {
    case None        => throw SysConfig.NotFound(module, key)
    case Some(entry) => entry
  }

  def find(
    module: String,
    ids: Iterable[String]
  ): Future[List[SysConfigEntry]] = CQL {
    select
      .where(_.module eqs module)
      .and(_.key in ids.toList.distinct)
  }.fetch()

  def save(entry: SysConfigEntry): Future[SysConfigEntry] = CQL {
    update
      .where(_.module eqs entry.module)
      .and(_.key eqs entry.key)
      .modify(_.value setTo entry.value)
  }.future().map(_ => entry)

  def remove(module: String, key: String): Future[ResultSet] = CQL {
    delete.where(_.module eqs module).and(_.key eqs key)
  }.future()

  def getOrElseUpdate[T](
    module: String,
    key: String,
    op: => T
  )(implicit serializer: Stringifier[T]): Future[T] = {
    for {
      maybe <- CQL {
        select(_.value)
          .where(_.module eqs module)
          .and(_.key eqs key)
      }.one()
      value <- maybe match {
        case None    =>
          val v: T = op
          persist(module, key, v >>: serializer).flatMap { applied =>
            if (applied) Future.successful(v)
            else getOrElseUpdate(module, key, op)
          }
        case Some(v) =>
          Future.successful(serializer << v)
      }
    } yield value
  }

  def persist(
    module: String,
    key: String,
    value: String
  ): Future[Boolean] = CQL {
    insert
      .value(_.module, module)
      .value(_.key, key)
      .value(_.value, value)
      .ifNotExists()
  }.future().map(_.wasApplied())
}