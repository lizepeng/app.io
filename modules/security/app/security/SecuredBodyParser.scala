package security

import helpers.BasicPlayApi
import models._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class SecuredBodyParser[A](
  action: (CheckedActions) => CheckedAction,
  onUnauthorized: RequestHeader => Result = req => Results.NotFound,
  onPermDenied: RequestHeader => Result = req => Results.NotFound,
  onBaseException: RequestHeader => Result = req => Results.NotFound
)(bodyParser: RequestHeader => User => Future[BodyParser[A]])(
  implicit
  val resource: CheckedResource,
  val basicPlayApi: BasicPlayApi,
  val _users: Users,
  val _accessControls: AccessControls
) extends PermissionCheckedBodyParser[A] {

  override def parser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = bodyParser(req)(user)
}