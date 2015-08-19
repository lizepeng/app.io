package plugins.akka.persistence.cassandra

import java.nio.ByteBuffer

import com.websudos.phantom.dsl._
import helpers.ExtEnumeratee._
import helpers._
import models.cassandra._
import play.api.libs.iteratee.{Enumeratee => _, _}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object JournalRecord {

  type Key = (String, Long, Long)
  type Value = (Boolean, ByteBuffer)
}

sealed class JournalTable
  extends CassandraTable[JournalTable, JournalRecord.Value] {

  override val tableName = "akka_persistence_journal"

  object persistence_id
    extends StringColumn(this)
    with PartitionKey[String]

  object partition_nr
    extends LongColumn(this)
    with PartitionKey[Long]

  object sequence_nr
    extends LongColumn(this)
    with ClusteringOrder[Long]

  object deleted
    extends BooleanColumn(this)

  object message
    extends BlobColumn(this)

  override def fromRow(r: Row): JournalRecord.Value = (deleted(r), message(r))
}

class Journal(
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends JournalTable
  with ExtCQL[JournalTable, JournalRecord.Value]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  import JournalRecord._

  def createIfNotExists(): Future[ResultSet] = create.ifNotExists.future()

  def save(
    messages: TraversableOnce[(Key, ByteBuffer)]
  ): Future[ResultSet] = {
    def cql_save(pid: String, pnr: Long, snr: Long, msg: ByteBuffer) =
      insert
        .value(_.persistence_id, pid)
        .value(_.partition_nr, pnr)
        .value(_.sequence_nr, snr)
        .value(_.deleted, false)
        .value(_.message, msg)

    CQL {
      (Batch.logged /: messages) {
        case (bq, ((pid, pnr, snr), msg)) =>
          bq.add(cql_save(pid, pnr, snr, msg))
      }
    }.future()
  }

  def remove(
    keys: TraversableOnce[Key],
    permanent: Boolean
  ): Future[ResultSet] = {
    def cql_del(pid: String, pnr: Long, snr: Long) =
      delete
        .where(_.persistence_id eqs pid)
        .and(_.partition_nr eqs pnr)
        .and(_.sequence_nr eqs snr)

    def cql_mark(pid: String, pnr: Long, snr: Long) =
      update
        .where(_.persistence_id eqs pid)
        .and(_.partition_nr eqs pnr)
        .and(_.sequence_nr eqs snr)
        .modify(_.deleted setTo true)

    CQL {
      if (permanent)
        (Batch.logged /: keys) {
          case (bq, (pid, pnr, snr)) => bq.add(cql_del(pid, pnr, snr))
        }
      else
        (Batch.logged /: keys) {
          case (bq, (pid, pnr, snr)) => bq.add(cql_mark(pid, pnr, snr))
        }
    }.future()
  }

  def streamMessages(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    max: Long
  )(maxPartitionSize: Int): Enumerator[JournalRecord.Value] = {
    DivideRange(fromSequenceNr, toSequenceNr, maxPartitionSize) &>
      Enumeratee.mapFlatten { case (from, to) =>
        select
          .where(_.persistence_id eqs persistenceId)
          .and(_.partition_nr eqs to + 1 - maxPartitionSize)
          .and(_.sequence_nr gte from)
          .and(_.sequence_nr lte to)
          .fetchEnumerator()
      } &>
      Enumeratee.take[JournalRecord.Value](max)
  }

  def streamKeys(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    max: Long
  )(maxPartitionSize: Int): Enumerator[Key] = {
    DivideRange(fromSequenceNr, toSequenceNr, maxPartitionSize) &>
      Enumeratee.mapFlatten { case (from, to) =>
        select(_.persistence_id, _.partition_nr, _.sequence_nr)
          .where(_.persistence_id eqs persistenceId)
          .and(_.partition_nr eqs to + 1 - maxPartitionSize)
          .and(_.sequence_nr gte from)
          .and(_.sequence_nr lte to)
          .fetchEnumerator()
      } &>
      Enumeratee.take[Key](max)
  }

  object DivideRange {

    def apply(from: Long, to: Long, span: Long): Enumerator[(Long, Long)] =
      Enumerator.unfold(from) { prev =>
        val ceil = prev / span * span + span - 1
        if (prev > to) None
        else if (ceil > to) Some(ceil + 1, (prev, to))
        else Some(ceil + 1, (prev, ceil))
      }
  }

}