package models

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class ChatMessage(
  to: UUID,
  from: UUID,
  text: String,
  sent_at: Option[DateTime] = None
) extends MessageLike

trait ChatHistoryCanonicalNamed extends CanonicalNamed {

  override val basicName = "chat_histories"
}

sealed class ChatHistoryTable
  extends NamedCassandraTable[ChatHistoryTable, ChatMessage]
  with ChatHistoryCanonicalNamed {

  object participant1_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object participant2_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object sent_at
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime]

  object sender
    extends UUIDColumn(this)

  object text
    extends StringColumn(this)

  override def fromRow(r: Row): ChatMessage = {
    val p1 = participant1_id(r)
    val p2 = participant2_id(r)
    val s = sender(r)
    ChatMessage(s, if (s != p1) p1 else p2, text(r), Some(sent_at(r)))
  }
}

object ChatHistory
  extends ChatHistoryCanonicalNamed

class ChatHistories(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends ChatHistoryTable
  with ExtCQL[ChatHistoryTable, ChatMessage]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def save(msg: ChatMessage): Future[ResultSet] = {
    val (p1, p2) = genKey(msg)
    CQL {
      insert
        .value(_.participant1_id, p1)
        .value(_.participant2_id, p2)
        .value(_.sender, msg.from)
        .value(_.text, msg.text)
        .value(_.sent_at, DateTime.now)
    }.future()
  }

  private def genKey(msg: ChatMessage): (UUID, UUID) =
    genKey(msg.from, msg.to)

  private def genKey(from: UUID, to: UUID): (UUID, UUID) =
    from.compareTo(to) match {
      case -1 => (from, to)
      case _  => (to, from)
    }
}