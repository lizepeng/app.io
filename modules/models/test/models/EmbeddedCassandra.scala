package models

import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.websudos.phantom.connectors._
import helpers._
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.specs2.specification.BeforeAfterAll
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.concurrent.ActorSystemProvider
import play.api._

/**
 * @author zepeng.li@gmail.com
 */
trait EmbeddedCassandra extends BeforeAfterAll with DefaultPlayExecutor {

  val playEnv = Environment.simple()

  val playConf = Configuration(
    ConfigFactory.parseString(
      """
       | app.domain = "app.io"
       | play.akka {
       |   actor-system = "application"
       |   shutdown-timeout = null
       |   config = "akka"
       | }
       | akka {
       | }
      """.stripMargin
    )
  )

  val playAppLifecycle = new DefaultApplicationLifecycle

  val actorSystem = new ActorSystemProvider(
    playEnv, playConf, playAppLifecycle
  ).get

  implicit lazy val basicPlayApi: BasicPlayApi = BasicPlayApi(
    langs = null,
    messagesApi = null,
    environment = null,
    configuration = playConf,
    applicationLifecycle = playAppLifecycle,
    actorSystem = actorSystem,
    ActorMaterializer()(actorSystem)
  )

  implicit lazy val contactPoint: KeySpaceBuilder = new KeySpaceBuilder(
    _.addContactPoint("localhost").withPort(9142)
  )

  implicit lazy val keySpaceDef: KeySpaceDef = contactPoint.keySpace("test")

  def beforeAll(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra("cassandra-test.yaml")
  }

  def afterAll(): Unit = {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }
}