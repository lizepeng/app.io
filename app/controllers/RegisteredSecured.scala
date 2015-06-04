package controllers

import play.api.i18n.MessagesApi
import play.api.libs.json._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class RegisteredSecured(
  val messagesApi: MessagesApi,
  val modules: Seq[PermissionCheckable]
) {

  object Modules {

    def names: Seq[String] = modules.map(_.CheckedModuleName.name)

    def toJson = Json.prettyPrint(
      JsObject(
        names.map { name =>
          (name, JsString(messagesApi(s"$name.name")))
        }
      )
    )
  }

  object Actions {

    def names: Seq[String] = actions.map(_.name)

    def toJson = Json.prettyPrint(
      JsObject(
        names.map { name =>
          (name, JsString(messagesApi(s"actions.$name")))
        }
      )
    )

    lazy val actions: Seq[CheckedAction] = CheckedActions.ALL
  }

}