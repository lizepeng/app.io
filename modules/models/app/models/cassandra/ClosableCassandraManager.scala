package models.cassandra

import java.net.InetSocketAddress

import com.websudos.phantom.connectors.DefaultCassandraManager
import helpers._
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */

class ClosableCassandraManager(
  val applicationLifecycle: ApplicationLifecycle
)
  extends DefaultCassandraManager(
    Set(new InetSocketAddress("localhost", 9042))
  )
  with Logging {

  applicationLifecycle.addStopHook { () =>
    Logger.info("Shutdown Cassandra Client")
    Future.successful {
      session.close()
      cluster.close()
    }
  }
}