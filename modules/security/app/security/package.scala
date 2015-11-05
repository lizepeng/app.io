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

  //we need this implicit method for convenience
  //if method has multiple implicit parameters
  implicit def authenticatedUser(implicit req: UserRequest[_]): User = req.user
}