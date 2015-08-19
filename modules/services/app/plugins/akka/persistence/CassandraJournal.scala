package plugins.akka.persistence

import java.nio.ByteBuffer

import akka.actor.{Actor, Stash}
import akka.pattern.pipe
import akka.persistence._
import akka.persistence.journal.AsyncWriteJournal
import akka.serialization.SerializationExtension
import com.datastax.driver.core.utils.Bytes
import helpers._
import models.actors.ResourcesMediator
import models.cassandra.KeySpaceBuilder
import play.api.libs.iteratee._
import plugins.akka.persistence.cassandra._

import scala.collection.immutable.Seq
import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class CassandraJournal extends AsyncWriteJournal with Stash {

  import context.dispatcher

  val maxPartitionSize = 5000000
  val batchSize        = 1000
  val mediator         = context.actorSelection(ResourcesMediator.actorPath)
  val serialization    = SerializationExtension(context.system)

  var journal   : Journal    = _
  var journalExt: JournalExt = _

  context become awaitingResources
  mediator ! ResourcesMediator.ModelRequired

  def awaitingResources: Actor.Receive = {
    case (bpa: BasicPlayApi, cp: KeySpaceBuilder) =>
      journal = new Journal(bpa, cp)
      journalExt = new JournalExt(bpa, cp)
      (for {
        _ <- journal.createIfNotExists()
        _ <- journalExt.createIfNotExists()
      } yield "ResourcesReady") pipeTo self

    case "ResourcesReady" =>
      unstashAll()
      context become receive

    case msg => stash()
  }

  override def asyncWriteMessages(messages: Seq[PersistentRepr]): Future[Unit] = {
    require(journal != null)
    require(journalExt != null)
    for {
      _ <- journal.save(
        messages.map { repr =>
          ((repr.persistenceId,
            repr.sequenceNr / maxPartitionSize,
            repr.sequenceNr),
            persistentToByteBuffer(repr))
        }
      )
      _ <- journalExt.batchSetHighestSequenceNumbers(
        messages.groupBy(_.persistenceId).mapValues(_.map(_.sequenceNr).max)
      )
    } yield Unit
  }

  override def asyncDeleteMessagesTo(
    persistenceId: String,
    toSequenceNr: Long,
    permanent: Boolean
  ): Future[Unit] = {
    require(journal != null)
    require(journalExt != null)
    for {
      _min <- journalExt.getLowestSequenceNr(persistenceId)
      _max <- journalExt.getHighestSequenceNr(persistenceId)
      ____ <- journalExt.setLowestSequenceNr(persistenceId, toSequenceNr + 1)
      unit <- journal.streamKeys(
        persistenceId, _min,
        Math.min(_max, toSequenceNr),
        toSequenceNr + 1 - _min
      )(maxPartitionSize) &>
        Enumeratee.grouped(Iteratee.takeUpTo(batchSize)) |>>>
        Iteratee.foreach(journal.remove(_, permanent))
    } yield unit
  }

  override def asyncReadHighestSequenceNr(
    persistenceId: String,
    fromSequenceNr: Long
  ): Future[Long] = {
    require(journalExt != null)
    journalExt.getHighestSequenceNr(persistenceId)
  }

  override def asyncReplayMessages(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    max: Long
  )(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    require(journal != null)
    for {
      _min <- journalExt.getLowestSequenceNr(persistenceId)
      _max <- journalExt.getHighestSequenceNr(persistenceId)
      unit <- journal.streamMessages(
        persistenceId,
        Math.max(_min, fromSequenceNr),
        Math.min(_max, toSequenceNr),
        max
      )(maxPartitionSize) &>
        Enumeratee.map[JournalRecord.Value] { case (deleted, byteBuffer) =>
          val repr = persistentFromByteBuffer(byteBuffer)
          if (!deleted) repr
          else repr.update(deleted = deleted)
        } |>>> Iteratee.foreach(replayCallback)
    } yield unit
  }

  private def persistentToByteBuffer(p: PersistentRepr): ByteBuffer =
    ByteBuffer.wrap(serialization.serialize(p).get)

  private def persistentFromByteBuffer(b: ByteBuffer): PersistentRepr = {
    serialization.deserialize(Bytes.getArray(b), classOf[PersistentRepr]).get
  }

  @deprecated("writeConfirmations will be removed, since Channels will be removed.", since = "2.3.4")
  override def asyncWriteConfirmations(
    confirmations: Seq[PersistentConfirmation]
  ): Future[Unit] = ???

  @deprecated("asyncDeleteMessages will be removed.", since = "2.3.4")
  override def asyncDeleteMessages(
    messageIds: Seq[PersistentId],
    permanent: Boolean
  ): Future[Unit] = ???
}