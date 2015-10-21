package controllers

import play.api.i18n.Messages
import play.api.libs.json._
import security.ModulesAccessControl._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class RegisteredSecured(
  val modules: PermissionCheckable*
) {

  object Modules {

    def names: Seq[String] = modules.map(_.CheckedModuleName.name)

    def toJson(implicit messages: Messages) = Json.prettyPrint(
      JsObject(
        names.map { name =>
          (name, JsString(messages(s"$name.name")))
        }
      )
    )
  }

  object Actions {

    def names: Seq[String] = actions.map(_.self.toString)

    def toJson(implicit messages: Messages) = Json.prettyPrint(
      JsObject(
        names.map { name =>
          (name, JsString(messages(s"actions.$name")))
        }
      )
    )

    lazy val actions: Seq[Access] = AccessDefinition.ALL
  }

}