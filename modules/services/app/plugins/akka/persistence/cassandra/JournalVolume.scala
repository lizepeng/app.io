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

  object Marker extends Enumeration {

    val Normal  = Value
    val Deleted = Value
    val Confirm = Value
  }
}

case class JournalVolumeRecord(
  sequence_nr: Long,
  marker: JournalVolumeRecord.Marker.Value,
  channel: Set[String],
  message: Option[ByteBuffer],
  purged: Boolean
)

sealed class JournalVolumeTable
  extends CassandraTable[JournalVolumeTable, JournalVolumeRecord] {

  import JournalVolumeRecord.Marker

  override val tableName = "akka_persistence_journal_volumes"

  object volume_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object sequence_nr
    extends LongColumn(this)
    with ClusteringOrder[Long]
    with Ascending

  object marker
    extends EnumColumn[JournalVolumeTable, JournalVolumeRecord, Marker.type](this, Marker)

  object channel
    extends SetColumn[JournalVolumeTable, JournalVolumeRecord, String](this)

  object purged
    extends OptionalBooleanColumn(this)

  object message
    extends OptionalBlobColumn(this)

  override def fromRow(r: Row): JournalVolumeRecord = JournalVolumeRecord(
    sequence_nr(r),
    marker(r),
    channel(r),
    message(r),
    purged(r).getOrElse(false)
  )
}

class JournalVolumes(
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends JournalVolumeTable
  with ExtCQL[JournalVolumeTable, JournalVolumeRecord]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  import JournalVolumeRecord._

  def createIfNotExists(): Future[ResultSet] = create.ifNotExists.future()

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
  ): Enumerator[JournalVolumeRecord] = CQL {
    select
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
        .value(_.message, Some(msg))
        .value(_.marker, Marker.Normal)

    CQL {
      (Batch.logged /: messages) {
        case (bq, ((vid, snr), msg)) =>
          bq.add {cql_save(vid, snr, msg)}
      }
    }.future()
  }

  def saveConfirm(
    messages: Traversable[(Key, String)]
  ): Future[ResultSet] = {
    def cql_save(vid: UUID, snr: Long, channel: String) =
      update
        .where(_.volume_id eqs vid)
        .and(_.sequence_nr eqs snr)
        .modify(_.marker setTo Marker.Confirm)
        .and(_.channel add channel)

    CQL {
      (Batch.logged /: messages) {
        case (bq, ((vid, snr), channel)) =>
          bq.add {cql_save(vid, snr, channel)}
      }
    }.future()
  }

  def remove(
    keys: Traversable[Key], permanent: Boolean
  ): Future[ResultSet] = {
    CQL {
      (Batch.logged /: keys) {
        case (bq, (vid, snr)) => bq.add {
          if (permanent) cql_del(vid, snr)
          else cql_mrk(vid, snr)
        }
      }
    }.future()
  }

  def removeOne(
    vid: UUID, snr: Long, permanent: Boolean
  ): Future[Unit] = {
    if (permanent) cql_del(vid, snr).future().map(_ => Unit)
    else cql_mrk(vid, snr).future().map(_ => Unit)
  }

  private def cql_mrk(vid: UUID, snr: Long) =
    CQL {
      update
        .where(_.volume_id eqs vid)
        .and(_.sequence_nr eqs snr)
        .modify(_.marker setTo Marker.Deleted)
    }

  private def cql_del(vid: UUID, snr: Long) =
    CQL {
      update
        .where(_.volume_id eqs vid)
        .and(_.sequence_nr eqs snr)
        .modify(_.purged setTo Some(true))
    }
}