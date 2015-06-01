package controllers

import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.libs.json._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object Secured {

  object Modules {

    def names: Seq[String] = modules.map(_.CheckedModuleName.name)

    def toJson = Json.prettyPrint(
      JsObject(
        names.map { name =>
          (name, JsString(Messages(s"$name.name")))
        }
      )
    )

    lazy val modules: Seq[PermissionCheckable] =
      Seq(
        Files,
        Groups,
        Users,
        EmailTemplates,
        AccessControls,
        controllers.api.Groups,
        controllers.api.Users,
        controllers.api.Search,
        controllers.api.Files,
        controllers.api.AccessControls
      )
  }

  object Actions {

    def names: Seq[String] = actions.map(_.name)

    def toJson = Json.prettyPrint(
      JsObject(
        names.map { name =>
          (name, JsString(Messages(s"actions.$name")))
        }
      )
    )

    lazy val actions: Seq[CheckedAction] = CheckedActions.ALL
  }

}