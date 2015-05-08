package security

import models.User
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.BodyParsers.parse
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait PermissionCheckedBodyParser[A]
  extends AuthorizedBodyParser[A] with PermissionCheck {

  def onPermDenied: RequestHeader => Result

  override def invokeParser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = {

    check(user).flatMap {
      case Some(true) => super.invokeParser(req)(user)
      case _          => Future.successful(
        parse.error(Future.successful(onPermDenied(req)))
      )
    }
  }
}
