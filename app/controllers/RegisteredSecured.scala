package controllers

import play.api.i18n.Messages
import play.api.libs.json._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class RegisteredSecured(
  val modules: PermissionCheckable*
) {

  object Modules {

    def names: Seq[String] = modules.map(_.checkedModuleName.name)

    def toJson(implicit messages: Messages) = Json.prettyPrint(
      JsObject(
        names.map { name =>
          name -> JsString(messages(s"$name.name"))
        }
      )
    )
  }

  object AccessDef {

    def toJson(implicit messages: Messages) = Json.prettyPrint(
      Json.toJson(
        modules.map { m =>
          val name = m.checkedModuleName.name
          name ->
            m.AccessDef.values.map { p =>
              p.self.toString -> messages(s"$name.ac.$p")
            }.toMap
        }.toMap
      )
    )
  }
}