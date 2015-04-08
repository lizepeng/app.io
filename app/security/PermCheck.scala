package security

import controllers._
import helpers.Logging
import models.AccessControl
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object PermCheck extends Logging {

  def apply(
    resource: String,
    onDenied: RequestHeader => Result
  ): ActionFunction[UserRequest, UserRequest] = {
    apply(Anything, onDenied)(CheckedResource(resource))
  }

  def apply(
    action: CheckedAction,
    onDenied: RequestHeader => Result = req => Results.Forbidden)(
    implicit resource: CheckedResource
  ): ActionFunction[UserRequest, UserRequest] =
    AuthCheck andThen new ActionFilter[UserRequest] {

      override protected def filter[A](
        req: UserRequest[A]
      ): Future[Option[Result]] = {
        val u = req.user.get

        val checked = for {
          b1 <- check(
            previous = None, action,
            ac => AccessControl.find(
              resource.name, ac.name, u.external_groups
            )
          )
          b2 <- check(
            previous = b1, action,
            ac => AccessControl.find(
              resource.name, ac.name, u.internal_groups
            )
          )
          b3 <- check(
            previous = b2, action,
            ac => AccessControl.find(
              resource.name, ac.name, u.id
            )
          )
        } yield b3

        checked.map {
          case Some(true) => None
          case _          => Some(onDenied(req))
        }
      }
    }

  private def check[A](
    previous: Option[Boolean],
    action: CheckedAction,
    tryToCheck: CheckedAction => Future[AccessControl.Granted[_]]
  ): Future[Option[Boolean]] = previous match {
    case Some(false) => Future.successful(previous)
    case b@_         => for {
      b1 <- toOption(tryToCheck(Anything)).map(_.orElse(b))
      b2 <-
      if (action == Anything) Future.successful(b1)
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
      case e: AccessControl.Denied[_]    => Logger.trace(e.reason); Some(false)
    }
}