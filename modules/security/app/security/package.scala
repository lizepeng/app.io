import helpers.BaseException
import models.User

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object security {

  /**
   * May be caused by [[User.SaltNotMatch]] or [[User.NoCredentials]]
   *
   * @see [[AuthenticateBySession.apply]]
   * @see [[AuthenticationCheck.onUnauthorized]]
   */
  case class Unauthorized()
    extends BaseException("security.unauthorized")

  case class CheckedAction(name: String) extends AnyVal

  case class CheckedResource(name: String) extends AnyVal

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
      Anything,
      Index,
      Show,
      NNew,
      Create,
      Edit,
      Save,
      Destroy,
      HistoryIndex
    )
  }

  object CheckedActions extends CheckedActions

  //we need this implicit method for convenience
  //if method has multiple implicit parameters
  implicit def authenticatedUser(implicit req: UserRequest[_]): User = req.user
}