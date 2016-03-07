package plugins.akka.persistence.cassandra

import java.nio.ByteBuffer

import akka.persistence.{SnapshotMetadata, SnapshotSelectionCriteria}
import com.websudos.phantom.dsl._
import helpers.ExtEnumeratee._
import helpers._
import models.cassandra._
import play.api.libs.iteratee.{Enumeratee => _, Enumerator}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class SnapshotRecord(
  metadata: SnapshotMetadata,
  snapshot: ByteBuffer
)

object SnapshotRecord {

}

sealed class SnapshotTable
  extends CassandraTable[SnapshotTable, SnapshotRecord] {

  override val tableName = "akka_persistence_snapshots"

  object persistence_id
    extends StringColumn(this)
    with PartitionKey[String]

  object sequence_nr
    extends LongColumn(this)
    with ClusteringOrder[Long] with Descending

  object timestamp
    extends LongColumn(this)

  object snapshot
    extends BlobColumn(this)

  override def fromRow(r: Row): SnapshotRecord = SnapshotRecord(
    SnapshotMetadata(
      persistence_id(r),
      sequence_nr(r),
      timestamp(r)
    ),
    snapshot(r)
  )
}

class Snapshots(
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends SnapshotTable
  with ExtCQL[SnapshotTable, SnapshotRecord]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  def createIfNotExists(): Future[ResultSet] = create.ifNotExists.future()

  def save(
    sr: SnapshotRecord
  ): Future[ResultSet] = CQL {
    insert
      .value(_.persistence_id, sr.metadata.persistenceId)
      .value(_.sequence_nr, sr.metadata.sequenceNr)
      .value(_.timestamp, sr.metadata.timestamp)
      .value(_.snapshot, sr.snapshot)
  }.future()

  def purge(persistenceId: String, sequenceNr: Long): Future[ResultSet] = CQL {
    cql_del(persistenceId, sequenceNr)
  }.future()

  def purge(keys: Traversable[(String, Long)]): Future[ResultSet] = CQL {
    (Batch.logged /: keys) {
      case (bq, (pid, snr)) => bq.add(cql_del(pid, snr))
    }
  }.future()

  private def cql_del(persistenceId: String, sequenceNr: Long) = {
    delete
      .where(_.persistence_id eqs persistenceId)
      .and(_.sequence_nr eqs sequenceNr)
  }

  def values(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Enumerator[SnapshotRecord] = CQL {
    select
      .where(_.persistence_id eqs persistenceId)
      .and(_.sequence_nr lte criteria.maxSequenceNr)
  }.fetchEnumerator() &>
    Enumeratee.filter(_.metadata.timestamp <= criteria.maxTimestamp)

  def keys(
    persistenceId: String,
    criteria: SnapshotSelectionCriteria
  ): Enumerator[(String, Long)] = CQL {
    select(_.persistence_id, _.sequence_nr, _.timestamp)
      .where(_.persistence_id eqs persistenceId)
      .and(_.sequence_nr lte criteria.maxSequenceNr)
  }.fetchEnumerator() &>
    Enumeratee.collect {
      case (pid, snr, ts) if ts <= criteria.maxTimestamp => (pid, snr)
    }
}