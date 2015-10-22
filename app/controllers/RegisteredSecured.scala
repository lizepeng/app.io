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

  object AccessDef {

    def names: Seq[String] = access_def.map(_.self.toString)

    def toJson(implicit messages: Messages) = Json.prettyPrint(
      JsArray(
        names.map { name => JsString(messages(s"actions.$name")) }
      )
    )

    lazy val access_def: Seq[Access] = AccessDefinition.ALL
  }

}