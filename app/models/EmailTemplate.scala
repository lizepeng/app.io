package models

import com.datastax.driver.core.Row
import com.datastax.driver.core.utils.UUIDs
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers.{Logging, _}
import models.cassandra._
import org.joda.time.DateTime
import play.api.i18n.Lang

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class EmailTemplate(
  id: UUID,
  lang: Lang,
  name: String,
  text: String,
  updated_on: DateTime = DateTime.now,
  updated_by: UUID,
  created_on: DateTime,
  created_by: UUID
)

case class EmailTemplateHistory(
  id: UUID,
  lang: Lang,
  name: String,
  text: String,
  updated_on: DateTime,
  updated_by: UUID
)

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

  object last_updated_on
    extends DateTimeColumn(this)
    with StaticColumn[DateTime]

  object created_on
    extends DateTimeColumn(this)
    with StaticColumn[DateTime]

  object created_by
    extends UUIDColumn(this)
    with StaticColumn[UUID]

}

trait EmailTemplateHistoryColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object updated_on
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime]

  object name
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
  with Logging {

  override def fromRow(r: Row): ET = ET(
    id(r),
    Lang(lang(r)),
    name(r),
    text(r),
    updated_on(r),
    updated_by(r),
    created_on(r),
    created_by(r)
  )
}

sealed class EmailTemplateHistories
  extends CassandraTable[EmailTemplateHistories, ETH]
  with EmailTemplateKey[EmailTemplateHistories, ETH]
  with EmailTemplateHistoryColumns[EmailTemplateHistories, ETH]
  with ExtCQL[EmailTemplateHistories, ETH]
  with Logging {

  override def fromRow(r: Row): ETH = ETH(
    id(r),
    Lang(lang(r)),
    name(r),
    text(r),
    updated_on(r),
    updated_by(r)
  )
}

object EmailTemplate extends EmailTemplates with Cassandra {

  case class NotFound(id: UUID)
    extends BaseException("not.found.email.template")

  case class UpdatedByOther()
    extends BaseException("email.template.updated.by.others")

  def apply(): ET = {
    val now = DateTime.now
    EmailTemplate(
      id = UUIDs.timeBased(),
      lang = Lang.defaultLang,
      name = "",
      text = "",
      created_on = now,
      updated_on = now,
      created_by = UUIDs.timeBased(),
      updated_by = UUIDs.timeBased()
    )
  }

  def find(id: UUID): Future[ET] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None       => throw NotFound(id)
    case Some(tmpl) => tmpl
  }

  def save(tmpl: ET): Future[ET] = for {
    init <- CQL {
      insert
        .value(_.id, tmpl.id)
        .value(_.last_updated_on, tmpl.updated_on)
        .value(_.created_on, tmpl.created_on)
        .value(_.created_by, tmpl.created_by)
        .ifNotExists()
    }.future().map(_.one.applied)
    tmpl <- CQL {
      val now = if (init) tmpl.created_on else DateTime.now()
      update
        .where(_.id eqs tmpl.id)
        .and(_.updated_on eqs now)
        .modify(_.name setTo tmpl.name)
        .and(_.text setTo tmpl.text)
        .and(_.updated_by setTo tmpl.updated_by)
        .onlyIf(_.last_updated_on eqs tmpl.updated_on)
    }.future().map(_.one).map { r =>
      if (r.applied) fromRow(r)
      else throw UpdatedByOther()
    }
  } yield tmpl

  def list(pager: Pager): Future[Iterator[ET]] = CQL {
    select.setFetchSize(2000)
  }.fetchEnumerator |>>>
    PIteratee.slice(pager.start, pager.limit)
}

object EmailTemplateHistory extends EmailTemplateHistories with Cassandra {

}