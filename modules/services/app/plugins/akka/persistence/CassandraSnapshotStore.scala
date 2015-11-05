package plugins.akka.persistence

import java.nio.ByteBuffer

import akka.actor.{Actor, Stash}
import akka.pattern.pipe
import akka.persistence._
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.serialization.SerializationExtension
import com.datastax.driver.core.utils.Bytes
import helpers.BasicPlayApi
import models.actors.ResourcesMediator
import models.cassandra.KeySpaceBuilder
import play.api.libs.iteratee._
import plugins.akka.persistence.cassandra._

import scala.concurrent.Future
import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
class CassandraSnapshotStore extends SnapshotStore with Stash {

  import ResourcesMediator._
  import context.dispatcher

  val maxLoadAttempts = 3
  val batchSize       = 1000
  val mediator        = context.actorSelection(ResourcesMediator.actorPath)
  val serialization   = SerializationExtension(context.system)

  var snapshots: Snapshots = _

  context become awaitingResources
  mediator ! List(GetBasicPlayApi, GetKeySpaceBuilder)

  def awaitingResources: Actor.Receive = {
    case List(bpa: BasicPlayApi, cp: KeySpaceBuilder) =>
      snapshots = new Snapshots(bpa, cp)
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
    snapshots.values(persistenceId, criteria) &>
      Enumeratee.take(maxLoadAttempts) &>
      Enumeratee.map { sr => (sr.metadata, deserialize(sr.snapshot)) } &>
      Enumeratee.collect { case (md, de) if de.isSuccess => SelectedSnapshot(md, de.get.data) } |>>>
      Iteratee.head[SelectedSnapshot]
  }

  override def saveAsync(
    metadata: SnapshotMetadata,
    snapshot: Any
  ): Future[Unit] = {
    snapshots.save(
      SnapshotRecord(metadata, serialize(Snapshot(snapshot)))
    ).map(_ => Unit)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    snapshots.purge(
      metadata.persistenceId, metadata.sequenceNr
    ).map(_ => Unit)
  }

  override def deleteAsync(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Future[Unit] = {
    //TODO
    // Unknown error occurred when using "cassandra-unit" combined with batch delete
    // A compromise is just delete multi snapshot one by one
    //    snapshots.keys(persistenceId, criteria) &>
    //      Enumeratee.grouped(Iteratee.takeUpTo(batchSize)) |>>>
    //      Iteratee.foreach(snapshots.purge(_))
    snapshots.keys(persistenceId, criteria) |>>>
      Iteratee.foreach { t => snapshots.purge(t._1, t._2) }
  }

  private def serialize(snapshot: Snapshot): ByteBuffer =
    ByteBuffer.wrap(serialization.findSerializerFor(snapshot).toBinary(snapshot))

  private def deserialize(bytes: ByteBuffer): Try[Snapshot] =
    serialization.deserialize(Bytes.getArray(bytes), classOf[Snapshot])
}