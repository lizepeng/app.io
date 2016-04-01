package models

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra._
import models.misc._
import org.joda.time.DateTime
import play.api.i18n._
import play.api.libs.iteratee.Enumeratee

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class EmailTemplate(
  id: String,
  lang: Lang,
  name: String,
  subject: String,
  to: String,
  text: String,
  created_at: DateTime,
  created_by: UUID,
  updated_at: DateTime,
  updated_by: UUID
) extends HasID[String] with TimeBased {

  def invalid = subject.isEmpty || to.isEmpty || text.isEmpty

  def save(
    implicit _emailTemplates: EmailTemplates
  ) = _emailTemplates.save(this)
}

case class EmailTemplateHistory(
  id: String,
  lang: Lang,
  name: String,
  subject: String,
  to: String,
  text: String,
  updated_at: DateTime,
  updated_by: UUID
) extends HasID[String]

trait EmailTemplateCanonicalNamed extends CanonicalNamed {

  override val basicName = "email_templates"
}

import models.{EmailTemplate => ET, EmailTemplateHistory => ETH}

trait EmailTemplateKey[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object id
    extends StringColumn(this)
    with PartitionKey[String]

  object lang
    extends StringColumn(this)
    with PartitionKey[String]

}

trait EmailTemplateColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object last_updated_at
    extends DateTimeColumn(this)
    with StaticColumn[DateTime]

  object created_at
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

  object email_subject
    extends StringColumn(this)

  object email_to
    extends StringColumn(this)

  object email_text
    extends StringColumn(this)

  object updated_by
    extends UUIDColumn(this)

}

sealed class EmailTemplateTable
  extends NamedCassandraTable[EmailTemplateTable, ET]
  with EmailTemplateCanonicalNamed
  with EmailTemplateKey[EmailTemplateTable, ET]
  with EmailTemplateColumns[EmailTemplateTable, ET]
  with EmailTemplateHistoryColumns[EmailTemplateTable, ET] {

  override def fromRow(r: Row): ET = ET(
    id(r),
    Lang(lang(r)),
    name(r),
    email_subject(r),
    email_to(r),
    email_text(r),
    created_at(r),
    created_by(r),
    updated_at(r),
    updated_by(r)
  )
}

object EmailTemplate
  extends EmailTemplateCanonicalNamed
  with ExceptionDefining {

  case class NotFound(id: String, lang: String)
    extends BaseException(error_code("not.found"))

  case class UpdatedByOther()
    extends BaseException(error_code("updated.by.others"))

  def nnew(
    id: String,
    lang: Lang,
    name: String,
    subject: String,
    to: String,
    text: String,
    created_at: DateTime,
    created_by: UUID
  ): EmailTemplate = {
    EmailTemplate(
      id = id,
      lang = lang,
      name = name,
      subject = subject,
      to = to,
      text = text,
      created_at = created_at,
      created_by = created_by,
      updated_at = created_at,
      updated_by = created_by
    )
  }
}

class EmailTemplates(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends EmailTemplateTable
  with ExtCQL[EmailTemplateTable, ET]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def find(
    id: String,
    lang: Lang,
    updated_at: Option[DateTime] = None
  ): Future[ET] = updated_at match {
    case Some(d) => find(id, lang, d)
    case None    => find(id, lang)
  }

  def find(id: String, lang: Lang, updated_at: DateTime): Future[ET] = {
    CQL {
      select
        .where(_.id eqs id)
        .and(_.lang eqs lang.code)
        .and(_.updated_at eqs updated_at)
    }.one().flatMap {
      case Some(tmpl)                       =>
        Future.successful(tmpl)
      case None if lang != Lang.defaultLang =>
        find(id, Lang.defaultLang, updated_at)
      case _                                =>
        throw EmailTemplate.NotFound(id, lang.code)
    }
  }

  def find(id: String, lang: Lang): Future[ET] = {
    CQL {
      select
        .where(_.id eqs id)
        .and(_.lang eqs lang.code)
    }.one().flatMap {
      case Some(tmpl)                       =>
        Future.successful(tmpl)
      case None if lang != Lang.defaultLang =>
        find(id, Lang.defaultLang)
      case _                                =>
        throw EmailTemplate.NotFound(id, lang.code)
    }
  }

  def getOrElseUpdate(id: String, lang: Lang)(
    systemAccount: => Future[User]
  ): Future[EmailTemplate] = {
    find(id, lang).recoverWith {
      case e: EmailTemplate.NotFound =>
        for {
          user <- systemAccount
          tmpl <- EmailTemplate.nnew(
            id = id,
            lang = lang,
            name = id,
            subject = "",
            to = "",
            text = "",
            created_at = DateTime.now,
            created_by = user.id
          ).save(this)
        } yield tmpl
    }
  }

  def save(tmpl: ET): Future[ET] = for {
    init <- CQL {
      insert
        .value(_.id, tmpl.id)
        .value(_.lang, tmpl.lang.code)
        .value(_.last_updated_at, tmpl.created_at)
        .value(_.created_at, tmpl.created_at)
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
          .and(_.email_subject setTo tmpl.subject)
          .and(_.email_to setTo tmpl.to)
          .and(_.email_text setTo tmpl.text)
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
      Enumeratee.mapM { case (id, lang) => find(id, Lang(lang)) } |>>>
      PIteratee.slice[ET](pager.start, pager.limit)
  }.map(_.toList)

  def destroy(id: String, lang: Lang): Future[ResultSet] = CQL {
    delete.where(_.id eqs id).and(_.lang eqs lang.code)
  }.future()

}

sealed class EmailTemplateHistoryTable
  extends NamedCassandraTable[EmailTemplateHistoryTable, ETH]
  with EmailTemplateCanonicalNamed
  with EmailTemplateKey[EmailTemplateHistoryTable, ETH]
  with EmailTemplateHistoryColumns[EmailTemplateHistoryTable, ETH] {

  override def fromRow(r: Row): ETH = ETH(
    id(r),
    Lang(lang(r)),
    name(r),
    email_subject(r),
    email_to(r),
    email_text(r),
    updated_at(r),
    updated_by(r)
  )
}

object EmailTemplateHistory
  extends EmailTemplateCanonicalNamed

class EmailTemplateHistories(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends EmailTemplateHistoryTable
  with ExtCQL[EmailTemplateHistoryTable, ETH]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  def list(id: String, lang: Lang, pager: Pager): Future[List[ETH]] = {
    CQL {
      select
        .where(_.id eqs id)
        .and(_.lang eqs lang.code)
    }.fetchEnumerator |>>>
      PIteratee.slice(pager.start, pager.limit)
  }.map(_.toList)
}