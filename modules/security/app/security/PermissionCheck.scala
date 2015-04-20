package security

import helpers.Logging
import models.AccessControl
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait PermissionCheck
  extends ActionFilter[UserRequest] with Logging {

  def action: CheckedActions => CheckedAction

  def onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result

  def resource: CheckedResource

  override protected def filter[A](
    req: UserRequest[A]
  ): Future[Option[Result]] = {
    val u = req.user
    val act = action(CheckedActions)

    val checked = for {
      b1 <- check(
        previous = None, act,
        ac => AccessControl.find(
          resource.name, ac.name, u.groups
        )
      )
      b2 <- check(
        previous = b1, act,
        ac => AccessControl.find(
          resource.name, ac.name, u.id
        )
      )
    } yield b2

    checked.map {
      case Some(true) => None
      case _          => Some(onDenied(resource, act, req))
    }
  }

  private def check[A](
    previous: Option[Boolean],
    action: CheckedAction,
    tryToCheck: CheckedAction => Future[AccessControl.Granted[_]]
  ): Future[Option[Boolean]] = previous match {
    case Some(false) => Future.successful(previous)
    case b@_         => for {
      b1 <- toOption(tryToCheck(CheckedActions.Anything)).map(_.orElse(b))
      b2 <-
      if (action == CheckedActions.Anything) Future.successful(b1)
      else b1 match {
        case b@Some(false) => Future.successful(b)
        case None          => toOption(tryToCheck(action))
        case b@Some(true)  => toOption(tryToCheck(action)).map {_.orElse(b)}
      }
    } yield b2
  }

  private def toOption(f: => Future[AccessControl.Granted[_]]): Future[Option[Boolean]] =
    f.map {
      granted => Logger.debug(granted.reason); Some(granted.canAccess)
    }.recover {
      case e: AccessControl.Undefined[_] => Logger.trace(e.reason); None
      case e: AccessControl.Denied[_]    => Logger.warn(e.reason); Some(false)
    }
}

case class PermissionChecker(
  action: CheckedActions => CheckedAction,
  onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result,
  resource: CheckedResource
) extends PermissionCheck