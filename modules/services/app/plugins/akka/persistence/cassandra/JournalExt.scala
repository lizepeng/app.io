package plugins.akka.persistence.cassandra

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
sealed class JournalExtTable
  extends CassandraTable[JournalExtTable, String] {

  override val tableName = "akka_persistence_journal_ext"

  object persistence_id
    extends StringColumn(this)
    with PartitionKey[String]

  object highest_sequence_nr
    extends OptionalLongColumn(this)

  object lowest_sequence_nr
    extends OptionalLongColumn(this)

  override def fromRow(r: Row): String = persistence_id(r)
}

class JournalExt(
  val basicPlayApi: BasicPlayApi,
  val cassandraManager: CassandraManager
)
  extends JournalExtTable
  with ExtCQL[JournalExtTable, String]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  create.ifNotExists.future()

  //Highest

  def getHighestSequenceNr(
    persistenceId: String
  ): Future[Long] = CQL {
    select(_.highest_sequence_nr)
      .where(_.persistence_id eqs persistenceId)
  }.one().map(_.flatten.getOrElse(0L))

  def batchSetHighestSequenceNumbers(
    sequenceNumbers: TraversableOnce[(String, Long)]
  ): Future[ResultSet] = {
    def cql_update(pid: String, snr: Long) =
      update.where(_.persistence_id eqs pid)
        .modify(_.highest_sequence_nr setTo Some(snr))

    CQL {
      (Batch.logged /: sequenceNumbers) {
        case (bq, (pid, snr)) =>
          bq.add(cql_update(pid, snr))
      }
    }.future()
  }

  //Lowest

  def getLowestSequenceNr(
    persistenceId: String
  ): Future[Long] = CQL {
    select(_.lowest_sequence_nr)
      .where(_.persistence_id eqs persistenceId)
  }.one().map(_.flatten.getOrElse(0L))

  def setLowestSequenceNr(
    persistenceId: String,
    sequenceNr: Long
  ): Future[ResultSet] = CQL {
    update
      .where(_.persistence_id eqs persistenceId)
      .modify(_.lowest_sequence_nr setTo Some(sequenceNr))
  }.future()
}