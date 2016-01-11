package models

import com.typesafe.config.ConfigFactory
import helpers.{BasicPlayApi, DefaultPlayExecutor}
import models.cassandra.KeySpaceBuilder
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.specs2.specification.BeforeAfterAll
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.concurrent.ActorSystemProvider
import play.api.{Configuration, Environment}

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
    configuration = playConf,
    applicationLifecycle = playAppLifecycle,
    actorSystem = actorSystem
  )

  implicit lazy val contactPoint: KeySpaceBuilder = new KeySpaceBuilder(
    basicPlayApi.applicationLifecycle,
    _.addContactPoint("localhost").withPort(9142)
  )

  def beforeAll(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra()
  }

  def afterAll(): Unit = {
    contactPoint.sessionProvider.shutdown()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }
}