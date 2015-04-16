package models.cassandra

import java.io.IOException
import java.net.Socket

import com.datastax.driver.core.{Cluster, Session}
import com.websudos.phantom.zookeeper.{CassandraConnector, CassandraManager}

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
trait Cassandra
  extends CassandraConnector {
  val keySpace = "app"

  override def manager = ClosableCassandraManager

  override implicit def session: Session = {
    manager.initIfNotInited(keySpace)
    manager.session
  }

  def shutdown() = manager.shutdown()
}

object ClosableCassandraManager extends CassandraManager {

  val livePort     = 9042
  val embeddedPort = 9142

  def cassandraHost: String = "localhost"

  private[this]           var inited            = false
  @volatile private[this] var _session: Session = null
  @volatile private[this] var _cluster: Cluster = null

  def cassandraPort: Int = {
    try {
      new Socket(cassandraHost, livePort)
      livePort
    } catch {
      case ex: IOException => embeddedPort
    }
  }

  /**
   * This method tells the manager how to create a Cassandra cluster out of the provided settings.
   * It deals with the underlying Datastax Cluster builder with a set of defaults that can be easily overridden.
   *
   * The purpose of this method, beyond DRY, is to allow users to override the building of a cluster with whatever they need.
   * @return A reference to a Datastax cluster.
   */
  protected[this] def createCluster(): Cluster = {
    Cluster.builder()
      .addContactPoint(cassandraHost)
      .withPort(cassandraPort)
      .withoutJMXReporting()
      .withoutMetrics()
      .build()
  }

  def cluster = _cluster

  def session = _session

  def initIfNotInited(keySpace: String): Unit = CassandraInitLock.synchronized {
    if (!inited) {
      _session = blocking {
        _cluster = createCluster()
        val s = cluster.connect()
        s.execute(s"CREATE KEYSPACE IF NOT EXISTS $keySpace WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
        s.execute(s"USE $keySpace;")
        s
      }
      inited = true
    }
  }

  def shutdown() = blocking {
    session.close()
    cluster.close()
    inited = false
  }
}

private[cassandra] case object CassandraInitLock