package plugins.akka.persistence

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory
import helpers.BasicPlayApi
import models._
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
    implicit val _chatHistories: ChatHistories = null
    implicit val _mailInbox: MailInbox = null
    implicit val _mailSent: MailSent = null
    system.actorOf(ResourcesMediator.props, ResourcesMediator.basicName)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    contactPoint.sessionProvider.shutdown()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }
}