import controllers.UserRequest
import helpers.Logging
import models.User

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object security {

  case class CheckedAction(name: String)

  case class CheckedResource(name: String)

  trait PermCheckable {
    self: Logging =>

    implicit lazy val CheckedModuleName = CheckedResource(module_name)

    def CheckedActions: Seq[CheckedAction] = CommonActions
  }

  val NNew         = CheckedAction("nnew")
  val Create       = CheckedAction("create")
  val Edit         = CheckedAction("edit")
  val Save         = CheckedAction("save")
  val Destroy      = CheckedAction("destroy")
  val Index        = CheckedAction("index")
  val Show         = CheckedAction("show")
  val HistoryIndex = CheckedAction("history.index")
  val Anything     = CheckedAction("anything")

  val CommonActions = Seq(
    NNew,
    Create,
    Edit,
    Save,
    Destroy,
    Index,
    Show,
    HistoryIndex,
    Anything
  )

  implicit def authenticatedUser(implicit req: UserRequest[_]): User =
    req.user.getOrElse(throw User.IsNotLoggedIn())
}