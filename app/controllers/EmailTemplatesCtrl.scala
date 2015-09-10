package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import helpers._
import models._
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n._
import play.api.mvc.Controller
import security._
import views._

import scala.concurrent.Future
import scala.language.implicitConversions

class EmailTemplatesCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val _sessionData: SessionData,
  val _emailTemplates: EmailTemplates,
  val _emailTemplateHistories: EmailTemplateHistories
)
  extends Secured(EmailTemplatesCtrl)
  with Controller
  with BasicPlayComponents
  with UserActionComponents
  with UsersComponents
  with DefaultPlayExecutor
  with CanonicalNameBasedMessages
  with I18nSupport {

  val TemplateFM = Form[TemplateFD](
    mapping(
      "lang" -> nonEmptyText.verifying(Lang.get(_).isDefined),
      "name" -> nonEmptyText(6, 255),
      "subject" -> nonEmptyText(6, 255),
      "text" -> text
    )(TemplateFD.apply)(TemplateFD.unapply)
  )

  case class TemplateFD(
    lang: String,
    name: String,
    subject: String,
    text: String
  )

  implicit def tmpl2FormData(tmpl: EmailTemplate): TemplateFD = {
    TemplateFD(tmpl.lang.code, tmpl.name, tmpl.subject, tmpl.text)
  }

  def index(pager: Pager) =
    UserAction(_.Index).async { implicit req =>
      _emailTemplates.list(pager).map { list =>
        Ok(html.email_templates.index(Page(pager, list)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def show(id: UUID, lang: Lang, updated_at: Option[DateTime] = None) =
    UserAction(_.Show).async { implicit req =>
      for {
        tmpl <- _emailTemplates.find(id, lang, updated_at)
        usr1 <- _users.find(tmpl.updated_by)
        usr2 <- _users.find(tmpl.created_by)
      } yield Ok {
        html.email_templates.show(tmpl, usr1, usr2)
      }
    }

  def nnew() =
    UserAction(_.NNew).async { implicit req =>
      Future.successful {
        Ok(html.email_templates.nnew(TemplateFM))
      }
    }

  def create =
    UserAction(_.Create).async { implicit req =>
      val bound = TemplateFM.bindFromRequest()
      bound.fold(
        failure => Future.successful {
          BadRequest {
            html.email_templates.nnew(bound)
          }
        },
        success => {
          _emailTemplates.build(
            id = UUIDs.timeBased(),
            lang = Lang(success.lang),
            name = success.name,
            subject = success.subject,
            text = success.text,
            created_by = req.user.id,
            updated_by = req.user.id
          ).save.map { saved =>
            Redirect {
              routes.EmailTemplatesCtrl.index()
            }.flashing {
              AlertLevel.Success -> message("created", saved.name)
            }
          }
        }
      )
    }

  def edit(id: UUID, lang: Lang) =
    UserAction(_.Edit).async { implicit req =>
      val result =
        for {
          tmpl <- _emailTemplates.find(id, lang)
          usr1 <- _users.find(tmpl.updated_by)
          usr2 <- _users.find(tmpl.created_by)
          ____ <- _sessionData.set[DateTime](key_editing(id), tmpl.updated_at)
        } yield Ok {
          html.email_templates.edit(
            TemplateFM.fill(tmpl), tmpl, usr1, usr2
          )
        }
      result.recover {
        case e: EmailTemplate.NotFound => Redirect {
          routes.EmailTemplatesCtrl.nnew()
        }
      }
    }

  def save(id: UUID, lang: Lang) =
    UserAction(_.Save).async { implicit req =>
      val bound = TemplateFM.bindFromRequest()
      bound.fold(
        failure =>
          for {
            tmpl <- _emailTemplates.find(id, lang)
            usr1 <- _users.find(tmpl.updated_by)
            usr2 <- _users.find(tmpl.created_by)
          } yield BadRequest {
            html.email_templates.edit(bound, tmpl, usr1, usr2)
          },
        success => {
          _sessionData.get[DateTime](key_editing(id)).flatMap {
            case Some(d) =>
              for {
                tmpl <- _emailTemplates.find(id, lang, Some(d))
                done <- tmpl.copy(
                  name = success.name,
                  subject = success.subject,
                  text = success.text,
                  updated_by = req.user.id
                ).save
                usr1 <- _users.find(done.updated_by)
                usr2 <- _users.find(done.created_by)
                ____ <- _sessionData.remove(key_editing(id))
              } yield Redirect {
                routes.EmailTemplatesCtrl.edit(id, lang)
              }
            case None    => Future.successful(BadRequest)
          }.recover {
            case e: EmailTemplate.UpdatedByOther => Redirect {
              routes.EmailTemplatesCtrl.edit(id, lang)
            }
          }
        }
      )
    }

  def history(id: UUID, lang: Lang, pager: Pager) =
    UserAction(_.HistoryIndex).async { implicit req =>
      for {
        tmpl <- _emailTemplates.find(id, lang)
        list <- _emailTemplateHistories.list(id, lang, pager)
        usrs <- _users.find(list.map(_.updated_by))
          .map(_.map(u => (u.id, u)).toMap)
      } yield Ok {
        html.email_templates.history(
          tmpl, Page(pager, list), usrs
        )
      }
    }

  def destroy(id: UUID, lang: Lang) =
    UserAction(_.Destroy).async { implicit req => {
      for {
        tmpl <- _emailTemplates.find(id, lang)
        ___ <- _emailTemplates.destroy(id, lang)
      } yield RedirectToPreviousURI.getOrElse {
        Redirect(routes.EmailTemplatesCtrl.index())
      }.flashing {
        AlertLevel.Success -> message("deleted", tmpl.name)
      }
    }.recover {
      case e: EmailTemplate.NotFound => NotFound(e.message)
    }

    }

  private def key_editing(id: UUID) = s"$canonicalName - $id - version"
}

object EmailTemplatesCtrl
  extends Secured(EmailTemplate)
  with CanonicalNameBasedMessages
  with ViewMessages