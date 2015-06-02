package models.cassandra

import java.net.InetSocketAddress

import com.datastax.driver.core.Session
import com.websudos.phantom.connectors._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
trait Cassandra extends CassandraConnector {

  implicit val keySpace: KeySpace = KeySpace("app")

  override val manager = ClosableCassandraManager

  override implicit val session: Session = {
    manager.initIfNotInited(keySpace.name)
    manager.session
  }

  def shutdown() = manager.shutdown()
}

object ClosableCassandraManager
  extends DefaultCassandraManager(
    Set(new InetSocketAddress("localhost", 9042))
  ) {

  def shutdown() = blocking {
    session.close()
    cluster.close()
  }
}