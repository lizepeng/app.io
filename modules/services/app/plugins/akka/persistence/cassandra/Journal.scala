package plugins.akka.persistence.cassandra

import java.nio.ByteBuffer

import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.dsl._
import helpers.ExtEnumeratee.Enumeratee
import helpers._
import models.cassandra._
import play.api.libs.iteratee.{Enumeratee => _, _}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object JournalRecord {

  case class Key(persistence_id: String, volume_nr: Long)
}

sealed class JournalTable
  extends CassandraTable[JournalTable, UUID] {

  override val tableName = "akka_persistence_journals"

  object persistence_id
    extends StringColumn(this)
      with PartitionKey[String]

  object volume_nr
    extends LongColumn(this)
      with ClusteringOrder[Long]
      with Ascending

  object highest_sequence_nr
    extends LongColumn(this)
      with StaticColumn[Long]

  object volume_id
    extends UUIDColumn(this)

  override def fromRow(r: Row): UUID = volume_id(r)
}

class Journal(
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
) extends JournalTable
  with ExtCQL[JournalTable, UUID]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  val journalVolumes = new JournalVolumes(basicPlayApi, keySpaceDef)

  import JournalRecord._

  def createIfNotExists(): Future[ResultSet] =
    for {
      ret <- create.ifNotExists.future()
      ___ <- journalVolumes.createIfNotExists()
    } yield ret

  def save(
    messages: Traversable[(String, Long, ByteBuffer)]
  )(volume_size: Long): Future[ResultSet] = {
    for {
      map <- findVolumeIDs(messages)(volume_size)
      ret <- journalVolumes.save(
        messages.map {
          case (pid, snr, buff) =>
            ((map(Key(pid, snr / volume_size)), snr), buff)
        }
      )
    } yield ret
  }

  def remove(
    pid: String, to: Long
  )(batchSize: Int)(volume_size: Int): Future[Unit] = {
    for {
      hsn <- readHighestSequenceNr(pid, 0L)(volume_size)
      ___ <- {
        if (to >= hsn) saveHighestSequenceNr(pid, hsn)
        else Future(Unit)
      }
      ret <- CQL {
        select(_.volume_id)
          .where(_.persistence_id eqs pid)
          .and(_.volume_nr gte 0L)
          .and(_.volume_nr lte to)
      }.fetchEnumerator() &>
        Enumeratee.mapFlatten(vid => journalVolumes.keys(vid, to = to)) &>
        Enumeratee.takeWhile(_._2 <= to) &>
        Enumeratee.grouped(Iteratee.takeUpTo(batchSize)) |>>>
        Iteratee.foreach(journalVolumes.remove(_))
    } yield ret
  }

  private def saveHighestSequenceNr(
    pid: String, snr: Long
  ): Future[ResultSet] = {
    def cql_insert(pid: String, snr: Long) =
      insert.value(_.persistence_id, pid)
        .value(_.highest_sequence_nr, 0L)
        .ifNotExists()

    def cql_update(pid: String, snr: Long) =
      update
        .where(_.persistence_id eqs pid)
        .modify(_.highest_sequence_nr setTo snr)
        .onlyIf(_.highest_sequence_nr isLt snr)

    for {
      i <- CQL {cql_insert(pid, snr)}.future()
      u <- CQL {cql_update(pid, snr)}.future()
    } yield u
  }

  def readHighestSequenceNr(
    pid: String, from: Long
  )(volume_size: Long): Future[Long] = {
    for {
      hsn <- CQL {
        select(_.volume_id)
          .where(_.persistence_id eqs pid)
          .and(_.volume_nr gte from / volume_size)
      }.fetchEnumerator() &>
        Enumeratee.mapFlatten(vid => journalVolumes.keys(vid, from)) &>
        Enumeratee.map(_._2) |>>>
        Iteratee.fold(0L)((a, b) => Math.max(a, b))
      ret <- {
        if (hsn > 0) Future(hsn)
        else CQL {
          select(_.highest_sequence_nr)
            .where(_.persistence_id eqs pid)
        }.one.map(_.getOrElse(0L))
      }
    } yield ret
  }

  def stream(
    pid: String, from: Long, to: Long, max: Long
  )(volume_size: Long): Enumerator[ByteBuffer] = {
    val ceil =
      if (to > Long.MaxValue - volume_size) Long.MaxValue
      else (to / volume_size + 1) * volume_size
    CQL {
      select(_.volume_id)
        .where(_.persistence_id eqs pid)
        .and(_.volume_nr gte from / volume_size)
        .and(_.volume_nr lte ceil)
    }.fetchEnumerator() &>
      Enumeratee.mapFlatten(vid => journalVolumes.values(vid, from, to)) &>
      Enumeratee.take[ByteBuffer](max)
  }

  private def findVolumeID(key: Key): Future[(Key, UUID)] = CQL {
    select(_.volume_id)
      .where(_.persistence_id eqs key.persistence_id)
      .and(_.volume_nr eqs key.volume_nr)
  }.one.flatMap {
    case None      => saveVolumeID(key)
    case Some(vid) => Future.successful(key -> vid)
  }

  private def saveVolumeID(key: Key): Future[(Key, UUID)] = {
    val uuid = UUIDs.timeBased
    CQL {
      insert.
        value(_.persistence_id, key.persistence_id)
        .value(_.volume_nr, key.volume_nr)
        .value(_.volume_id, uuid)
        .ifNotExists()
    }.future().map(_.wasApplied).flatMap { done =>
      if (!done) findVolumeID(key)
      else Future.successful(key -> uuid)
    }
  }

  private def findVolumeIDs(
    messages: Traversable[(String, Long, ByteBuffer)]
  )(volume_size: Long): Future[Map[Key, UUID]] = {
    Future.sequence(
      messages.map {
        case (pid, snr, _) =>
          Key(pid, snr / volume_size)
      }.toList.distinct.map(findVolumeID)
    ).map(_.toMap)
  }
}