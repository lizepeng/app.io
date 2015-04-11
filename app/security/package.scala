import controllers.UserRequest
import models.User

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object security {

  case class CheckedAction(name: String)

  case class CheckedResource(name: String)

  trait CheckedActions {

    val NNew         = CheckedAction("nnew")
    val Create       = CheckedAction("create")
    val Edit         = CheckedAction("edit")
    val Save         = CheckedAction("save")
    val Destroy      = CheckedAction("destroy")
    val Index        = CheckedAction("index")
    val Show         = CheckedAction("show")
    val HistoryIndex = CheckedAction("history.index")
    val Anything     = CheckedAction("anything")

    val ALL = Seq(
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
  }

  object CheckedActions extends CheckedActions

  implicit def authenticatedUser(implicit req: UserRequest[_]): User =
    req.user.getOrElse(throw AuthCheck.Unauthorized())
}