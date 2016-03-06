package plugins.akka.persistence

import akka.actor.{Actor, Props}
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.websudos.phantom.connectors._
import helpers.BasicPlayApi
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.junit.runner._
import org.specs2.runner._
import play.api._
import play.api.inject.DefaultApplicationLifecycle
import services.actors.ResourcesMediator

/**
 * @author zepeng.li@gmail.com
 */
@RunWith(classOf[JUnitRunner])
class CassandraSnapshotStoreSpec extends SnapshotStoreSpec {

  lazy override val config = ConfigFactory.parseString(
    """
     | akka.loggers = ["akka.testkit.TestEventListener"]
     | akka.stdout-loglevel = "OFF"
     | akka.loglevel = "OFF"
     |
     | akka.persistence.snapshot-store.plugin = "cassandra-snapshot-store"
     | cassandra-snapshot-store {
     |   class = "plugins.akka.persistence.CassandraSnapshotStore"
     | }
    """.stripMargin
  )

  def context: ApplicationLoader.Context =
    ApplicationLoader.createContext(
      new Environment(
        new java.io.File("."),
        ApplicationLoader.getClass.getClassLoader,
        Mode.Test
      )
    )

  implicit lazy val basicPlayApi: BasicPlayApi = BasicPlayApi(
    langs = null,
    messagesApi = null,
    environment = null,
    configuration = Configuration(
      ConfigFactory.parseString(
        """
         | app.domain = "app.io"
        """.stripMargin
      )
    ),
    applicationLifecycle = new DefaultApplicationLifecycle,
    actorSystem = system,
    ActorMaterializer()(system)
  )

  implicit lazy val contactPoint: KeySpaceBuilder = new KeySpaceBuilder(
    _.addContactPoint("localhost").withPort(9142)
  )

  implicit lazy val keySpaceDef: KeySpaceDef = contactPoint.keySpace("test")

  override protected def beforeAll(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra()
    system.actorOf(
      Props(
        new Actor {
          override def receive: Receive = {
            case _ => sender ! List(basicPlayApi, keySpaceDef)
          }
        }
      ), ResourcesMediator.basicName
    )
    super.beforeAll()
    Thread.sleep(3000) //wait until cassandra is ready
  }

  override def afterAll(): Unit = {
    super.afterAll()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }
}