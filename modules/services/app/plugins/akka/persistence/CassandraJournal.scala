package plugins.akka.persistence

import java.nio.ByteBuffer

import akka.actor._
import akka.pattern.pipe
import akka.persistence._
import akka.persistence.journal.AsyncWriteJournal
import akka.serialization.SerializationExtension
import com.datastax.driver.core.utils.Bytes
import com.websudos.phantom.connectors.KeySpaceDef
import helpers._
import play.api.libs.iteratee._
import plugins.akka.persistence.cassandra.JournalVolumeRecord.Marker
import plugins.akka.persistence.cassandra._
import services.actors.ResourcesMediator

import scala.collection.immutable.Seq
import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class CassandraJournal extends AsyncWriteJournal with Stash with ActorLogging {

  import ResourcesMediator._
  import context.dispatcher

  val volumeSize    = 500000
  val batchSize     = 1000
  val mediator      = context.actorSelection(ResourcesMediator.actorPath)
  val serialization = SerializationExtension(context.system)

  var journal: Journal = _

  context become awaitingResources
  mediator ! List(GetBasicPlayApi, GetKeySpaceDef)

  def awaitingResources: Actor.Receive = {
    case List(bpa: BasicPlayApi, cp: KeySpaceDef) =>
      journal = new Journal(bpa, cp)
      (for {
        _ <- journal.createIfNotExists()
      } yield "ResourcesReady") pipeTo self

    case "ResourcesReady" =>
      log.debug("journal table ready")
      unstashAll()
      context become receive

    case msg => stash()
  }

  override def asyncWriteMessages(
    messages: Seq[PersistentRepr]
  ): Future[Unit] = {
    for {
      _ <- journal.save(
        messages.map { repr =>
          (repr.persistenceId, repr.sequenceNr, persistentToByteBuffer(repr))
        }
      )(volumeSize)
    } yield Unit
  }

  override def asyncDeleteMessagesTo(
    persistenceId: String,
    toSequenceNr: Long,
    permanent: Boolean
  ): Future[Unit] = {
    journal.remove(persistenceId, toSequenceNr, permanent)(batchSize)
  }

  override def asyncReadHighestSequenceNr(
    persistenceId: String,
    fromSequenceNr: Long
  ): Future[Long] = {
    journal.readHighestSequenceNr(persistenceId, fromSequenceNr)(volumeSize)
  }

  override def asyncReplayMessages(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    max: Long
  )(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    journal.stream(
      persistenceId,
      fromSequenceNr,
      toSequenceNr,
      max
    )(volumeSize) &>
      Enumeratee.collect[JournalVolumeRecord] {
        case JournalVolumeRecord(snr, Marker.Normal, _, Some(buff), false)  =>
          persistentFromByteBuffer(buff)
        case JournalVolumeRecord(snr, Marker.Deleted, _, Some(buff), false) =>
          persistentFromByteBuffer(buff).update(deleted = true)
        case JournalVolumeRecord(snr, Marker.Confirm, channel, _, false)    =>
          PersistentRepr(
            payload = Unit,
            persistenceId = persistenceId,
            sequenceNr = snr,
            confirms = channel.toList
          )
      } |>>> Iteratee.foreach(replayCallback)
  }

  private def persistentToByteBuffer(p: PersistentRepr): ByteBuffer =
    ByteBuffer.wrap(serialization.serialize(p).get)

  private def persistentFromByteBuffer(b: ByteBuffer): PersistentRepr = {
    serialization.deserialize(Bytes.getArray(b), classOf[PersistentRepr]).get
  }

  @deprecated("writeConfirmations will be removed, since Channels will be removed.", since = "2.3.4")
  override def asyncWriteConfirmations(
    confirmations: Seq[PersistentConfirmation]
  ): Future[Unit] = {
    for {
      _ <- journal.saveConfirm(
        confirmations.map { repr =>
          (repr.persistenceId, repr.sequenceNr, repr.channelId)
        }
      )(volumeSize)
    } yield Unit
  }

  @deprecated("asyncDeleteMessages will be removed.", since = "2.3.4")
  override def asyncDeleteMessages(
    messageIds: Seq[PersistentId],
    permanent: Boolean
  ): Future[Unit] = {
    Future.sequence(
      messageIds.map { pid =>
        journal.removeOne(pid.persistenceId, pid.sequenceNr, permanent)(batchSize)
      }
    ).map(_ => Unit)
  }
}