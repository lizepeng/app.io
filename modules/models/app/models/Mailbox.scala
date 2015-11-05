package models

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Mail(
  to: Set[MailTo],
  from: UUID,
  subject: String = "",
  text: String = "",
  datetime: Option[DateTime] = None
) extends MessageLike

trait MailInboxCanonicalNamed extends CanonicalNamed {

  override val basicName = "mail_inbox"
}

sealed class MailInboxTable
  extends NamedCassandraTable[MailInboxTable, Mail]
  with MailInboxCanonicalNamed {

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object year
    extends IntColumn(this)
    with PartitionKey[Int]

  object received_at
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime]

  object mail_to
    extends JsonSetColumn[MailInboxTable, Mail, MailTo](this)
    with MailToJsonStringifier

  object mail_from
    extends UUIDColumn(this)

  object subject
    extends StringColumn(this)

  object text
    extends StringColumn(this)

  override def fromRow(r: Row): Mail = Mail(
    mail_to(r),
    mail_from(r),
    subject(r),
    text(r),
    Some(received_at(r))
  )
}

object MailInbox
  extends MailInboxCanonicalNamed

class MailInbox(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends MailInboxTable
  with ExtCQL[MailInboxTable, Mail]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def save(user_id: UUID, mail: Mail): Future[ResultSet] = {
    val now = DateTime.now
    CQL {
      insert
        .value(_.user_id, user_id)
        .value(_.year, now.getYear)
        .value(_.received_at, now)
        .value(_.mail_to, mail.to)
        .value(_.mail_from, mail.from)
        .value(_.subject, mail.subject)
        .value(_.text, mail.text)
    }.future()
  }
}

trait MailSentCanonicalNamed extends CanonicalNamed {

  override val basicName = "mail_sent"
}

sealed class MailSentTable
  extends NamedCassandraTable[MailSentTable, Mail]
  with MailSentCanonicalNamed {

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object year
    extends IntColumn(this)
    with PartitionKey[Int]

  object sent_at
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime]

  object mail_to
    extends JsonSetColumn[MailSentTable, Mail, MailTo](this)
    with MailToJsonStringifier

  object subject
    extends StringColumn(this)

  object text
    extends StringColumn(this)

  override def fromRow(r: Row): Mail = Mail(
    mail_to(r),
    user_id(r),
    subject(r),
    text(r),
    Some(sent_at(r))
  )
}

object MailSent
  extends MailSentCanonicalNamed

class MailSent(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends MailSentTable
  with ExtCQL[MailSentTable, Mail]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(create.ifNotExists.future())

  def save(mail: Mail): Future[ResultSet] = {
    val now = DateTime.now
    CQL {
      insert
        .value(_.user_id, mail.from)
        .value(_.year, now.getYear)
        .value(_.sent_at, now)
        .value(_.mail_to, mail.to)
        .value(_.subject, mail.subject)
        .value(_.text, mail.text)
    }.future()
  }
}