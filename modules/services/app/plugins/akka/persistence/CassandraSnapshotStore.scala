package plugins.akka.persistence

import java.nio.ByteBuffer

import akka.actor.{Actor, Stash}
import akka.pattern.pipe
import akka.persistence._
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.serialization.SerializationExtension
import com.datastax.driver.core.utils.Bytes
import com.websudos.phantom.connectors.CassandraManager
import helpers.BasicPlayApi
import models.actors.ResourcesMediator
import play.api.libs.iteratee._
import plugins.akka.persistence.cassandra._

import scala.concurrent.Future
import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
class CassandraSnapshotStore extends SnapshotStore with Stash {

  import context.dispatcher

  val maxLoadAttempts = 3
  val batchSize       = 1000
  val mediator        = context.actorSelection(ResourcesMediator.actorPath)
  val serialization   = SerializationExtension(context.system)

  var snapshots: Snapshots = _

  context become awaitingResources
  mediator ! ResourcesMediator.ModelRequired

  def awaitingResources: Actor.Receive = {
    case (bpa: BasicPlayApi, cm: CassandraManager) =>
      snapshots = new Snapshots(bpa, cm)
      (for {
        _ <- snapshots.createIfNotExists()
      } yield "ResourcesReady") pipeTo self

    case "ResourcesReady" =>
      unstashAll()
      context become receive

    case msg => stash()
  }

  override def loadAsync(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] = {
    require(snapshots != null)
    snapshots.stream(persistenceId, criteria) &>
      Enumeratee.take(maxLoadAttempts) &>
      Enumeratee.map { sr => (sr.metadata, deserialize(sr.snapshot)) } &>
      Enumeratee.collect { case (md, de) if de.isSuccess => SelectedSnapshot(md, de.get.data) } |>>>
      Iteratee.head[SelectedSnapshot]
  }

  override def saveAsync(
    metadata: SnapshotMetadata,
    snapshot: Any
  ): Future[Unit] = {
    require(snapshots != null)
    snapshots.save(
      SnapshotRecord(metadata, serialize(Snapshot(snapshot)))
    ).map(_ => Unit)
  }

  override def saved(metadata: SnapshotMetadata): Unit = {}

  override def delete(metadata: SnapshotMetadata): Unit = {
    require(snapshots != null)
    snapshots.purge(metadata.persistenceId, metadata.sequenceNr)
  }

  override def delete(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Unit = {
    require(snapshots != null)
    snapshots.streamKeys(persistenceId, criteria) &>
      Enumeratee.grouped(Iteratee.takeUpTo(batchSize)) |>>>
      Iteratee.foreach(snapshots.purge(_))
  }

  private def serialize(snapshot: Snapshot): ByteBuffer =
    ByteBuffer.wrap(serialization.findSerializerFor(snapshot).toBinary(snapshot))

  private def deserialize(bytes: ByteBuffer): Try[Snapshot] =
    serialization.deserialize(Bytes.getArray(bytes), classOf[Snapshot])
}