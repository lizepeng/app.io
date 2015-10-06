package plugins.akka.persistence

import java.nio.ByteBuffer

import akka.actor._
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

import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
class CassandraJournal extends AsyncWriteJournal with Stash with ActorLogging {

  import context.dispatcher

  val volumeSize    = 500000
  val batchSize     = 1000
  val mediator      = context.actorSelection(ResourcesMediator.actorPath)
  val serialization = SerializationExtension(context.system)

  var journal: Journal = _

  context become awaitingResources
  mediator ! ResourcesMediator.ModelRequired

  def awaitingResources: Actor.Receive = {
    case (bpa: BasicPlayApi, cp: KeySpaceBuilder) =>
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

  def asyncWriteMessages(
    messages: immutable.Seq[AtomicWrite]
  ): Future[immutable.Seq[Try[Unit]]] = {
    def asyncWrite(messages: Seq[PersistentRepr]): Future[Try[Unit]] = {
      for {
        m <- Future {
          messages.map { repr =>
            (repr.persistenceId,
              repr.sequenceNr,
              persistentToByteBuffer(repr))
          }
        }
        _ <- journal.save(m)(volumeSize)
      } yield Success[Unit](Unit)
    }.recover {
      case e: Throwable => Failure(e)
    }

    Future.sequence {
      messages.map(_.payload).map(asyncWrite)
    }
  }

  override def asyncDeleteMessagesTo(
    persistenceId: String,
    toSequenceNr: Long
  ): Future[Unit] = {
    journal.remove(persistenceId, toSequenceNr)(batchSize)
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
      Enumeratee.map[ByteBuffer] { case byteBuffer =>
        persistentFromByteBuffer(byteBuffer)
      } |>>> Iteratee.foreach(replayCallback)
  }

  private def persistentToByteBuffer(p: PersistentRepr): ByteBuffer =
    ByteBuffer.wrap(serialization.serialize(p).get)

  private def persistentFromByteBuffer(b: ByteBuffer): PersistentRepr = {
    serialization.deserialize(Bytes.getArray(b), classOf[PersistentRepr]).get
  }
}