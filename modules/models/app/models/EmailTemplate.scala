package models

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra._
import org.joda.time.DateTime
import play.api.i18n.Lang
import play.api.libs.iteratee.Enumeratee

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class EmailTemplate(
  id: UUID,
  lang: Lang,
  name: String,
  subject: String,
  text: String,
  updated_at: DateTime,
  updated_by: UUID,
  created_by: UUID
) extends HasUUID with TimeBased {

  def save(implicit emailTemplateRepo: EmailTemplates) = emailTemplateRepo.save(this)
}

case class EmailTemplateHistory(
  id: UUID,
  lang: Lang,
  name: String,
  subject: String,
  text: String,
  updated_at: DateTime,
  updated_by: UUID
) extends HasUUID

import models.{EmailTemplate => ET, EmailTemplateHistory => ETH}

trait EmailTemplateKey[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  override val tableName = "email_templates"

  object id
    extends TimeUUIDColumn(this)
    with PartitionKey[UUID]

  object lang
    extends StringColumn(this)
    with PartitionKey[String]

}

trait EmailTemplateColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object last_updated_at
    extends DateTimeColumn(this)
    with StaticColumn[DateTime]

  object created_by
    extends UUIDColumn(this)
    with StaticColumn[UUID]

}

trait EmailTemplateHistoryColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object updated_at
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime]

  object name
    extends StringColumn(this)

  object subject
    extends StringColumn(this)

  object text
    extends StringColumn(this)

  object updated_by
    extends UUIDColumn(this)

}

sealed class EmailTemplateTable
  extends CassandraTable[EmailTemplateTable, ET]
  with EmailTemplateKey[EmailTemplateTable, ET]
  with EmailTemplateColumns[EmailTemplateTable, ET]
  with EmailTemplateHistoryColumns[EmailTemplateTable, ET]
  with CanonicalNamedModel[ET]
  with ExceptionDefining
  with Logging {

  override def fromRow(r: Row): ET = ET(
    id(r),
    Lang(lang(r)),
    name(r),
    subject(r),
    text(r),
    updated_at(r),
    updated_by(r),
    created_by(r)
  )
}

object EmailTemplate
  extends EmailTemplateTable
  with ExceptionDefining {

  case class NotFound(id: UUID, lang: String)
    extends BaseException(error_code("not.found"))

  case class UpdatedByOther()
    extends BaseException(error_code("updated.by.others"))

}

class EmailTemplates(
  implicit
  val basicPlayApi: BasicPlayApi,
  val cassandraManager: CassandraManager
)
  extends EmailTemplateTable
  with ExtCQL[EmailTemplateTable, ET]
  with BasicPlayComponents
  with CassandraComponents {

  create.ifNotExists.future()

  def build(
    id: UUID,
    lang: Lang,
    name: String,
    subject: String,
    text: String,
    updated_by: UUID,
    created_by: UUID
  ): EmailTemplate = {
    EmailTemplate(
      id = id,
      lang = lang,
      name = name,
      subject = subject,
      text = text,
      updated_at = TimeBased.extractDatetime(id),
      updated_by = updated_by,
      created_by = created_by
    )
  }

  def find(
    id: UUID, lang: Lang,
    updated_at: Option[DateTime] = None
  ): Future[ET] =
    find(id, lang.code, updated_at)

  def find(
    id: UUID, lang: String,
    updated_at: Option[DateTime]
  ): Future[ET] =
    CQL {

      updated_at
        .map(
          d => select
            .where(_.id eqs id)
            .and(_.lang eqs lang).and(_.updated_at eqs d)
        )
        .getOrElse(
          select
            .where(_.id eqs id)
            .and(_.lang eqs lang)
        )
    }.one().map {
      case None       => throw EmailTemplate.NotFound(id, lang)
      case Some(tmpl) => tmpl
    }.recoverWith {
      case e: EmailTemplate.NotFound if lang != Lang.defaultLang.code =>
        this.find(id, Lang.defaultLang, updated_at)
    }

  def save(tmpl: ET): Future[ET] = for {
    init <- CQL {
      insert
        .value(_.id, tmpl.id)
        .value(_.lang, tmpl.lang.code)
        .value(_.last_updated_at, tmpl.created_at)
        .value(_.created_by, tmpl.created_by)
        .ifNotExists()
    }.future().map(_.wasApplied())
    tmpl <- {
      val curr = if (init) tmpl.created_at else DateTime.now
      CQL {
        update
          .where(_.id eqs tmpl.id)
          .and(_.lang eqs tmpl.lang.code)
          .and(_.updated_at eqs curr)
          .modify(_.name setTo tmpl.name)
          .and(_.subject setTo tmpl.subject)
          .and(_.text setTo tmpl.text)
          .and(_.last_updated_at setTo curr)
          .and(_.updated_by setTo tmpl.updated_by)
          .onlyIf(_.last_updated_at is tmpl.updated_at)
      }.future().map { r =>
        if (r.wasApplied()) tmpl.copy(updated_at = curr)
        else throw EmailTemplate.UpdatedByOther()
      }
    }
  } yield tmpl

  def list(pager: Pager): Future[List[ET]] = {
    CQL {select(_.id, _.lang).distinct}.fetchEnumerator &>
      Enumeratee.mapM { case (id, lang) => find(id, lang, None) } |>>>
      PIteratee.slice[ET](pager.start, pager.limit)
  }.map(_.toList)

  def destroy(id: UUID, lang: Lang): Future[ResultSet] = CQL {
    delete.where(_.id eqs id).and(_.lang eqs lang.code)
  }.future()

}

sealed class EmailTemplateHistoryTable
  extends CassandraTable[EmailTemplateHistoryTable, ETH]
  with EmailTemplateKey[EmailTemplateHistoryTable, ETH]
  with EmailTemplateHistoryColumns[EmailTemplateHistoryTable, ETH]
  with CanonicalNamedModel[ETH]
  with Logging {

  override def fromRow(r: Row): ETH = ETH(
    id(r),
    Lang(lang(r)),
    name(r),
    subject(r),
    text(r),
    updated_at(r),
    updated_by(r)
  )
}

object EmailTemplateHistory
  extends EmailTemplateHistoryTable

class EmailTemplateHistories(
  implicit
  val basicPlayApi: BasicPlayApi,
  val cassandraManager: CassandraManager
)
  extends EmailTemplateHistoryTable
  with ExtCQL[EmailTemplateHistoryTable, ETH]
  with BasicPlayComponents
  with CassandraComponents {

  def list(id: UUID, lang: Lang, pager: Pager): Future[List[ETH]] = {
    CQL {
      select
        .where(_.id eqs id)
        .and(_.lang eqs lang.code)
    }.fetchEnumerator |>>>
      PIteratee.slice(pager.start, pager.limit)
  }.map(_.toList)
}