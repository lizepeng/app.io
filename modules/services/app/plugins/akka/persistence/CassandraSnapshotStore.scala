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

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
object CassandraSnapshotStore {
  case class DeletedMeta(metadata: SnapshotMetadata)
  case class DeletedCriteria(criteria: SnapshotSelectionCriteria)
}

class CassandraSnapshotStore extends SnapshotStore with Stash {

  import CassandraSnapshotStore._
  import context.dispatcher

  val maxLoadAttempts = 3
  val batchSize       = 1000
  val mediator        = context.actorSelection(ResourcesMediator.actorPath)
  val serialization   = SerializationExtension(context.system)

  var snapshots: Snapshots = _

  var deletingMetadata: mutable.Set[SnapshotMetadata]          = mutable.Set.empty
  var deletingCriteria: mutable.Set[SnapshotSelectionCriteria] = mutable.Set.empty

  context become awaitingResources
  mediator ! ResourcesMediator.ModelRequired

  def awaitingResources: Actor.Receive = {
    case (bpa: BasicPlayApi, cp: KeySpaceBuilder) =>
      snapshots = new Snapshots(bpa, cp)
      (for {
        _ <- snapshots.createIfNotExists()
      } yield "ResourcesReady") pipeTo self

    case "ResourcesReady" =>
      unstashAll()
      context become receiveWithAsyncDeleting

    case msg => stash()
  }

  override def loadAsync(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] = {
    val immutableDeletingMetadata = deletingMetadata.toSet
    val immutableDeletingCriteria = deletingCriteria.toSet
    snapshots.stream(persistenceId, criteria, maxLoadAttempts) &>
      Enumeratee.filterNot { sr =>
        (false /: immutableDeletingMetadata.map {_ == sr.metadata})(_ || _)
      } &>
      Enumeratee.filterNot { sr =>
        (false /: immutableDeletingCriteria.map { c =>
          sr.metadata.sequenceNr <= c.maxSequenceNr &&
            sr.metadata.timestamp <= c.maxTimestamp
        })(_ || _)
      } &>
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

  override def saved(metadata: SnapshotMetadata): Unit = {}

  override def delete(metadata: SnapshotMetadata): Unit = {
    deletingMetadata += metadata
    snapshots
      .purge(metadata.persistenceId, metadata.sequenceNr)
      .onComplete {
      case _ => self ! DeletedMeta(metadata)
    }

  }

  override def delete(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Unit = {
    deletingCriteria += criteria
    (snapshots.streamKeys(persistenceId, criteria) &>
      Enumeratee.grouped(Iteratee.takeUpTo(batchSize)) |>>>
      Iteratee.foreach(snapshots.purge(_))
      ).onComplete {
      case _ => self ! DeletedCriteria(criteria)
    }
  }

  def receiveWithAsyncDeleting: Actor.Receive = receive.orElse {
    case DeletedMeta(metadata)     => deletingMetadata -= metadata
    case DeletedCriteria(criteria) => deletingCriteria -= criteria
  }

  private def serialize(snapshot: Snapshot): ByteBuffer =
    ByteBuffer.wrap(serialization.findSerializerFor(snapshot).toBinary(snapshot))

  private def deserialize(bytes: ByteBuffer): Try[Snapshot] =
    serialization.deserialize(Bytes.getArray(bytes), classOf[Snapshot])
}