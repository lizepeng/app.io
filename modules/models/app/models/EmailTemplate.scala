package models

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import com.websudos.phantom.query.SelectWhere
import helpers.{Logging, _}
import models.cassandra._
import org.joda.time.DateTime
import play.api.Play.current
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
) extends HasUUID {

  def save = EmailTemplate.save(this)
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

sealed class EmailTemplates
  extends CassandraTable[EmailTemplates, ET]
  with EmailTemplateKey[EmailTemplates, ET]
  with EmailTemplateColumns[EmailTemplates, ET]
  with EmailTemplateHistoryColumns[EmailTemplates, ET]
  with ExtCQL[EmailTemplates, ET]
  with Module[ET]
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

object EmailTemplate extends EmailTemplates with Cassandra with AppConfig {

  case class NotFound(id: UUID, lang: String)
    extends BaseException(msg_key("not.found"))

  case class UpdatedByOther()
    extends BaseException(msg_key("updated.by.others"))

  def apply(
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
      val cql: SelectWhere[EmailTemplates, ET] = select
        .where(_.id eqs id)
        .and(_.lang eqs lang)

      updated_at
        .map(d => cql.and(_.updated_at eqs d))
        .getOrElse(cql)
    }.one().map {
      case None       => throw NotFound(id, lang)
      case Some(tmpl) => tmpl
    }.recoverWith {
      case e: NotFound if lang != Lang.defaultLang.code =>
        EmailTemplate.find(id, Lang.defaultLang, updated_at)
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
          .onlyIf(_.last_updated_at eqs tmpl.updated_at)
      }.future().map { r =>
        if (r.wasApplied()) tmpl.copy(updated_at = curr)
        else throw UpdatedByOther()
      }
    }
  } yield tmpl

  def list(pager: Pager): Future[List[ET]] = {
    CQL {
      distinct(_.id, _.lang).setFetchSize(fetchSize())
    }.fetchEnumerator &>
      Enumeratee.mapM { case (id, lang) => find(id, lang, None) } |>>>
      PIteratee.slice[ET](pager.start, pager.limit)
  }.map(_.toList)

  def destroy(id: UUID, lang: Lang): Future[ResultSet] = CQL {
    delete.where(_.id eqs id).and(_.lang eqs lang.code)
  }.future()

}

sealed class EmailTemplateHistories
  extends CassandraTable[EmailTemplateHistories, ETH]
  with EmailTemplateKey[EmailTemplateHistories, ETH]
  with EmailTemplateHistoryColumns[EmailTemplateHistories, ETH]
  with ExtCQL[EmailTemplateHistories, ETH]
  with Module[ETH]
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

object EmailTemplateHistory extends EmailTemplateHistories with Cassandra with
AppConfig {

  def list(id: UUID, lang: Lang, pager: Pager): Future[List[ETH]] = {
    CQL {
      select
        .where(_.id eqs id)
        .and(_.lang eqs lang.code)
        .setFetchSize(fetchSize())
    }.fetchEnumerator |>>>
      PIteratee.slice(pager.start, pager.limit)
  }.map(_.toList)
}