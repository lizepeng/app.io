package plugins.akka.persistence.cassandra

import java.nio.ByteBuffer

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import play.api.libs.iteratee.Enumerator

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object JournalVolumeRecord {

  type Key = (UUID, Long)
}

sealed class JournalVolumeTable
  extends CassandraTable[JournalVolumeTable, ByteBuffer] {

  override val tableName = "akka_persistence_journal_volumes"

  object volume_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object sequence_nr
    extends LongColumn(this)
    with ClusteringOrder[Long]
    with Ascending

  object message
    extends BlobColumn(this)

  override def fromRow(r: Row): ByteBuffer = message(r)
}

class JournalVolumes(
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends JournalVolumeTable
  with ExtCQL[JournalVolumeTable, ByteBuffer]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  import JournalVolumeRecord._

  def createIfNotExists(): Future[ResultSet] = CQL(create.ifNotExists).future()

  def keys(
    vid: UUID, from: Long = 0L, to: Long = Long.MaxValue
  ): Enumerator[(UUID, Long)] = CQL {
    select(_.volume_id, _.sequence_nr)
      .where(_.volume_id eqs vid)
      .and(_.sequence_nr gte from)
      .and(_.sequence_nr lte to)
  }.fetchEnumerator()

  def values(
    vid: UUID, from: Long = 0L, to: Long = Long.MaxValue
  ): Enumerator[ByteBuffer] = CQL {
    select(_.message)
      .where(_.volume_id eqs vid)
      .and(_.sequence_nr gte from)
      .and(_.sequence_nr lte to)
  }.fetchEnumerator()

  def save(
    messages: Traversable[(Key, ByteBuffer)]
  ): Future[ResultSet] = {
    def cql_save(vid: UUID, snr: Long, msg: ByteBuffer) =
      insert
        .value(_.volume_id, vid)
        .value(_.sequence_nr, snr)
        .value(_.message, msg)

    CQL {
      (Batch.logged /: messages) {
        case (bq, ((vid, snr), msg)) =>
          bq.add(cql_save(vid, snr, msg))
      }
    }.future()
  }

  def remove(
    keys: Traversable[Key]
  ): Future[ResultSet] = {
    def cql_del(vid: UUID, snr: Long) =
      delete
        .where(_.volume_id eqs vid)
        .and(_.sequence_nr eqs snr)

    CQL {
      (Batch.logged /: keys) {
        case (bq, (vid, snr)) => bq.add(cql_del(vid, snr))
      }
    }.future()
  }
}