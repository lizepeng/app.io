package controllers

import helpers._
import models._
import models.misc._
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
) extends EmailTemplateCanonicalNamed
  with CheckedModuleName
  with Controller
  with BasicPlayComponents
  with UserActionComponents[AccessControlsCtrl.AccessDef]
  with AccessControlsCtrl.AccessDef
  with ExceptionHandlers
  with UsersComponents
  with DefaultPlayExecutor
  with CanonicalNameBasedMessages
  with I18nSupport {

  val TemplateFM = Form[TemplateFD](
    mapping(
      "lang" -> nonEmptyText.verifying(Lang.get(_).isDefined),
      "name" -> nonEmptyText(6, 255),
      "subject" -> nonEmptyText(6, 512),
      "to" -> nonEmptyText(6, 512),
      "text" -> nonEmptyText(6, 8192)
    )(TemplateFD.apply)(TemplateFD.unapply)
  )

  case class TemplateFD(
    lang: String,
    name: String,
    subject: String,
    to: String,
    text: String
  )

  implicit def tmpl2FormData(tmpl: EmailTemplate): TemplateFD = {
    TemplateFD(
      lang = tmpl.lang.code,
      name = tmpl.name,
      subject = tmpl.subject,
      to = tmpl.to,
      text = tmpl.text
    )
  }

  def index(pager: Pager) =
    UserAction(_.P03).async { implicit req =>
      _emailTemplates.list(pager).map { list =>
        Ok(html.email_templates.index(Page(pager, list)))
      }
    }

  def show(id: String, lang: Lang, updated_at: Option[DateTime] = None) =
    UserAction(_.P02).async { implicit req =>
      for {
        tmpl <- _emailTemplates.find(id, lang, updated_at)
        usr1 <- _users.find(tmpl.updated_by)
        usr2 <- _users.find(tmpl.created_by)
      } yield Ok {
        html.email_templates.show(tmpl, usr1, usr2)
      }
    }

  def nnew() =
    UserAction(_.P00).async { implicit req =>
      Future.successful {
        Ok(html.email_templates.nnew(TemplateFM))
      }
    }

  def create =
    UserAction(_.P01).async { implicit req =>
      val bound = TemplateFM.bindFromRequest()
      bound.fold(
        failure => Future.successful {
          BadRequest {
            html.email_templates.nnew(bound)
          }
        },
        success => {
          EmailTemplate.nnew(
            id = success.name,
            lang = Lang(success.lang),
            name = success.name,
            subject = success.subject,
            to = success.to,
            text = success.text,
            created_at = DateTime.now,
            created_by = req.user.id
          ).save.map { saved =>
            Redirect {
              routes.EmailTemplatesCtrl.index()
            }.flashing {
              AlertLevel.Success -> message("created", saved.name)
            }
          }.recover {
            case e: EmailTemplate.UpdatedByOther => Redirect {
              routes.EmailTemplatesCtrl.show(success.name, Lang(success.lang))
            }.flashing {
              AlertLevel.Danger -> message("exists", success.name)
            }
          }
        }
      )
    }

  def edit(id: String, lang: Lang) =
    UserAction(_.P04).async { implicit req =>
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
          routes.EmailTemplatesCtrl.index()
        }
      }
    }

  def save(id: String, lang: Lang) =
    UserAction(_.P05).async { implicit req =>
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
                tmpl <- _emailTemplates.find(id, lang, d)
                done <- tmpl.copy(
                  name = success.name,
                  subject = success.subject,
                  to = success.to,
                  text = success.text,
                  updated_by = req.user.id
                ).save
                usr1 <- _users.find(done.updated_by)
                usr2 <- _users.find(done.created_by)
                ____ <- _sessionData.remove(key_editing(id))
              } yield Redirect {
                routes.EmailTemplatesCtrl.show(id, lang)
              }.flashing {
                AlertLevel.Success -> message("saved", id)
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

  def history(id: String, lang: Lang, pager: Pager) =
    UserAction(_.P07).async { implicit req =>
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

  def destroy(id: String, lang: Lang) =
    UserAction(_.P06).async { implicit req =>
      (for {
        tmpl <- _emailTemplates.find(id, lang)
        ___ <- _emailTemplates.destroy(id, lang)
      } yield RedirectToPreviousURI.getOrElse {
        Redirect(routes.EmailTemplatesCtrl.index())
      }.flashing {
        AlertLevel.Success -> message("deleted", tmpl.name)
      }).recover {
        case e: EmailTemplate.NotFound => Redirect {
          routes.EmailTemplatesCtrl.index()
        }
      }
    }

  private def key_editing(id: String) = s"$canonicalName - $id - version"
}

object EmailTemplatesCtrl
  extends EmailTemplateCanonicalNamed
    with PermissionCheckable
    with CanonicalNameBasedMessages
    with ViewMessages {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    def values = Seq(P00, P01, P02, P03, P04, P05, P06, P07)
  }

  object AccessDef extends AccessDef
}