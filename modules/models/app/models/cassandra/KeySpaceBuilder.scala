package models.cassandra

import com.datastax.driver.core._
import com.websudos.phantom.connectors._
import helpers.Logging
import play.api.inject.ApplicationLifecycle

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
 * Fixing thread leaking in dev mode
 *
 * @since phantom-1.11.0
 * @see [[com.websudos.phantom.connectors.KeySpaceBuilder]]
 */
class KeySpaceBuilder(
  applicationLifecycle: ApplicationLifecycle,
  clusterBuilder: ClusterBuilder
) extends Logging {

  applicationLifecycle.addStopHook(
    () => Future.successful {
      sessionProvider.shutdown()
      Logger.info("Shutdown Phantom Cassandra Driver")
    }
  )

  /**
   * Specify an additional builder to be applied when creating the Cluster instance.
   * This hook exposes the underlying Java API of the builder API of the Cassandra
   * driver.
   */
  def withClusterBuilder(builder: ClusterBuilder): KeySpaceBuilder =
    new KeySpaceBuilder(applicationLifecycle, clusterBuilder andThen builder)

  lazy val sessionProvider = new ClosableSessionProvider(clusterBuilder)

  /**
   * Create a new keySpace with the specified name.
   */
  def keySpace(name: String): KeySpaceDef =
    new KeySpaceDef(name, sessionProvider)

}

/**
 * Fixing thread leaking in dev mode
 *
 * @since phantom-1.11.0
 * @see [[com.websudos.phantom.connectors.DefaultSessionProvider]]
 */
class ClosableSessionProvider(builder: ClusterBuilder) extends SessionProvider {

  val sessionCache = new Cache[String, Session]

  lazy val cluster: Cluster = {
    // TODO - the original phantom modules had .withoutJMXReporting().withoutMetrics() as defaults, discuss best choices
    val cb = Cluster.builder
    builder(cb).build
  }

  /**
   * Initializes the keySpace with the given name on
   * the specified Session.
   */
  protected def initKeySpace(session: Session, keySpace: String): Session = {
    session.execute(s"CREATE KEYSPACE IF NOT EXISTS $keySpace WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
    session
  }

  /**
   * Creates a new Session for the specified keySpace.
   */
  protected[this] def createSession(keySpace: String): Session = {
    val session = cluster.connect
    initKeySpace(session, keySpace)
  }

  def getSession(keySpace: String): Session = {
    sessionCache.getOrElseUpdate(keySpace, createSession(keySpace))
  }

  def shutdown(): Unit = {
    sessionCache.foreach(_.close())
    cluster.close()
  }
}

/**
 * Fixing thread leaking in dev mode
 *
 * @since phantom-1.11.0
 * @see [[com.websudos.phantom.connectors.Cache]]
 */
class Cache[K, V] {

  private[this] val map = TrieMap[K, Lazy]()

  private[this] class Lazy(value: => V) {

    lazy val get: V = value
  }

  /**
   * Get the element for the specified key
   * if it has already been set or otherwise
   * associate the key with the given (lazy) value.
   *
   * @return the value previously associated with the key
   *         or (if no value had been previously set) the specified new value.
   */
  def getOrElseUpdate(key: K, op: => V): V = {
    val lazyOp = new Lazy(op)
    map.putIfAbsent(key, lazyOp) match {
      case Some(oldval) =>
        // don't evaluate the new lazyOp, return existing value
        oldval.get
      case _            =>
        // no existing value for key, evaluate lazyOp
        lazyOp.get
    }
  }

  def foreach(op: V => Unit): Unit = {
    map.values.foreach(lz => op(lz.get))
  }
}