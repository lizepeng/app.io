package controllers

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.session._
import helpers._
import models.EmailTemplate.{NotFound, UpdatedByOther}
import models._
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Lang, Messages => MSG}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import security._
import views._

import scala.concurrent.Future
import scala.language.implicitConversions

object EmailTemplates extends Controller with Logging with AppConfig {

  override val module_name: String = "controllers.email_templates"

  val TemplateFM = Form[TemplateFD](
    mapping(
      "lang" -> nonEmptyText.verifying(Lang.get(_).isDefined),
      "name" -> nonEmptyText(6, 255),
      "text" -> text
    )(TemplateFD.apply)(TemplateFD.unapply)
  )

  case class TemplateFD(lang: String, name: String, text: String)

  implicit def tmpl2FormData(tmpl: EmailTemplate): TemplateFD = {
    TemplateFD(tmpl.lang.code, tmpl.name, tmpl.text)
  }

  def index(pager: Pager) = (UserAction >> PermCheck(
    module_name, "index",
    onDenied = req => Forbidden
  )).async { implicit req =>
    EmailTemplate.list(pager).map { list =>
      Ok(html.email_templates.index(Page(pager, list)))
    }.recover {
      case e: BaseException => NotFound
    }
  }

  def show(id: UUID, lang: Lang, updated_on: Option[DateTime] = None) = (UserAction >> PermCheck(
    module_name, "show",
    onDenied = req => Forbidden
  )).async { implicit req =>
    println(updated_on)
    for {
      tmpl <- EmailTemplate.find(id, lang, updated_on)
      usr1 <- User.find(tmpl.updated_by)
      usr2 <- User.find(tmpl.created_by)
    } yield Ok {
      html.email_templates.show(tmpl, usr1, usr2)
    }
  }

  def nnew() = (UserAction >> PermCheck(
    module_name, "nnew",
    onDenied = req => Forbidden
  )) { implicit req =>
    Ok(html.email_templates.nnew(TemplateFM))
  }

  def create = (UserAction >> PermCheck(
    module_name, "create",
    onDenied = req => Forbidden
  )).async { implicit req =>
    val bound = TemplateFM.bindFromRequest()
    bound.fold(
      failure => Future.successful {
        BadRequest {
          html.email_templates.nnew(bound)
        }
      },
      success => {
        val now = DateTime.now
        EmailTemplate(
          id = UUIDs.timeBased(),
          lang = Lang(success.lang),
          name = success.name,
          text = success.text,
          created_on = now,
          updated_on = now,
          created_by = req.user.get.id,
          updated_by = req.user.get.id
        ).save.map { saved =>
          Redirect {
            routes.EmailTemplates.index()
          }.flashing {
            AlertLevel.Success -> MSG(s"$module_name.created", saved.name)
          }
        }
      }
    )
  }

  def edit(id: UUID, lang: Lang) = (UserAction >> PermCheck(
    module_name, "edit",
    onDenied = req => Forbidden
  )).async { implicit req =>
    val result =
      for {
        tmpl <- EmailTemplate.find(id, lang)
        usr1 <- User.find(tmpl.updated_by)
        usr2 <- User.find(tmpl.created_by)
        ____ <- SessionData.set[DateTime](key_editing(id), tmpl.updated_on)
      } yield Ok {
        html.email_templates.edit(
          TemplateFM.fill(tmpl), tmpl, usr1, usr2
        )
      }
    result.recover {
      case e: NotFound => Redirect {
        routes.EmailTemplates.nnew()
      }
    }
  }

  def save(id: UUID, lang: Lang) = (UserAction >> PermCheck(
    module_name, "save",
    onDenied = req => Forbidden
  )).async { implicit req =>
    val bound = TemplateFM.bindFromRequest()
    bound.fold(
      failure =>
        for {
          tmpl <- EmailTemplate.find(id, lang)
          usr1 <- User.find(tmpl.updated_by)
          usr2 <- User.find(tmpl.created_by)
        } yield BadRequest {
          html.email_templates.edit(bound, tmpl, usr1, usr2)
        },
      success => {
        SessionData.get[DateTime](key_editing(id)).flatMap {
          case Some(d) =>
            for {
              tmpl <- EmailTemplate.find(id, lang, Some(d))
              done <- tmpl.copy(
                name = success.name,
                text = success.text,
                updated_by = req.user.get.id
              ).save
              usr1 <- User.find(done.updated_by)
              usr2 <- User.find(done.created_by)
              ____ <- SessionData.remove(key_editing(id))
            } yield Redirect {
              routes.EmailTemplates.edit(id, lang)
            }
          case None    => Future.successful(BadRequest)
        }.recover {
          case e: UpdatedByOther => Redirect {
            routes.EmailTemplates.edit(id, lang)
          }
        }
      }
    )
  }

  def history(id: UUID, lang: Lang, pager: Pager) = (UserAction >> PermCheck(
    module_name, "history",
    onDenied = req => Forbidden
  )).async { implicit req =>
    for {
      list <- EmailTemplateHistory.list(id, lang, pager)
      usrs <- User.find(list.map(_.updated_by))
    } yield Ok {
      html.email_templates.history(
        id, lang, Page(pager, list), usrs
      )
    }
  }

  def destroy(id: UUID, lang: Lang) = (UserAction >> PermCheck(
    module_name, "destroy",
    onDenied = req => Forbidden
  )).async { implicit req => {
    for {
      tmpl <- EmailTemplate.find(id, lang)
      ___ <- EmailTemplate.destroy(id, lang)
    } yield RedirectToPreviousURI.getOrElse {
      Redirect(routes.EmailTemplates.index())
    }.flashing {
      AlertLevel.Success -> MSG(s"$module_name.deleted", tmpl.name)
    }
  }.recover {
    case e: NotFound => NotFound(MSG(s"$module_name.not.found", id))
  }

  }

  private def key_editing(id: UUID) = s"$module_name - $id - version"
}