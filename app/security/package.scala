import controllers.UserRequest
import models.User

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object security {
  implicit def authenticatedUser(implicit req: UserRequest[_]): User = req.user.get
}