package models.sys

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.Assignment
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import common.Logging
import models.cassandra.Cassandra
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class SysConfig(
  module_name: String,
  timestamp: DateTime,
  cfs_root: UUID
)

sealed class SysConfigs extends CassandraTable[SysConfigs, SysConfig] {

  override val tableName = "system_config"

  object module
    extends StringColumn(this)
    with PartitionKey[String]

  object timestamp
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime] with Ascending

  object cfs_root extends UUIDColumn(this)

  override def fromRow(r: Row): SysConfig = {
    SysConfig(module(r), timestamp(r), cfs_root(r))
  }
}

object SysConfig extends SysConfigs with Logging with Cassandra {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  Await.result(create.future(), 500 millis)

  type COL[T] = (SysConfigs) => SelectColumnRequired[SysConfigs, SysConfig, T]

  def get[T](module: String, column: COL[T]): Future[Option[T]] = {
    select(column).where(_.module eqs module).one()
  }

  def put(
    module: String,
    timestamp: DateTime,
    updates: ((SysConfigs) => Assignment)*
  ) = {
    val stmt = update
      .where(_.module eqs module)
      .and(_.timestamp eqs timestamp)

    updates.toList match {
      case Nil          => Future.successful(None)
      case head :: tail => {
        (stmt.modify(head) /: tail)(_ and _)
      }.future().map(Some(_))
    }
  }
}
