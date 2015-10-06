package plugins.akka.persistence

import akka.actor.{Actor, Props}
import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory
import helpers.BasicPlayApi
import models.actors.ResourcesMediator
import models.cassandra.KeySpaceBuilder
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.junit.runner._
import org.specs2.runner._
import play.api._
import play.api.inject.DefaultApplicationLifecycle

/**
 * @author zepeng.li@gmail.com
 */
@RunWith(classOf[JUnitRunner])
class CassandraJournalSpec extends JournalSpec(
  config = ConfigFactory.parseString(
    """
     | akka.loggers = ["akka.testkit.TestEventListener"]
     | akka.stdout-loglevel = "OFF"
     | akka.loglevel = "OFF"
     |
     | akka.persistence.journal.plugin = "cassandra-journal"
     | cassandra-journal {
     |   class = "plugins.akka.persistence.CassandraJournal"
     | }
    """.stripMargin
  )
) {

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
    configuration = Configuration(
      ConfigFactory.parseString(
        """
         | app.domain = "app.io"
        """.stripMargin
      )
    ),
    applicationLifecycle = new DefaultApplicationLifecycle,
    actorSystem = system
  )

  implicit lazy val contactPoint: KeySpaceBuilder = new KeySpaceBuilder(
    basicPlayApi.applicationLifecycle,
    _.addContactPoint("localhost").withPort(9142)
  )

  override protected def beforeAll(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra()
    super.beforeAll()
    system.actorOf(
      Props(
        new Actor {
          override def receive: Receive = {
            case ResourcesMediator.ModelRequired => sender !(basicPlayApi, contactPoint)
          }
        }
      ), ResourcesMediator.basicName
    )
  }

  override def afterAll(): Unit = {
    super.afterAll()
    contactPoint.sessionProvider.shutdown()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }
}