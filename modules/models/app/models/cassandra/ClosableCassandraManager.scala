package models.cassandra

import java.net.InetSocketAddress

import com.websudos.phantom.connectors.DefaultCassandraManager
import helpers._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */

class ClosableCassandraManager(
  val basicPlayApi: BasicPlayApi
)
  extends DefaultCassandraManager(
    Set(new InetSocketAddress("localhost", 9042))
  )
  with BasicPlayComponents
  with Logging {

  applicationLifecycle.addStopHook { () =>
    Logger.info("Shutdown Cassandra Client")
    Future.successful {
      session.close()
      cluster.close()
    }
  }
}