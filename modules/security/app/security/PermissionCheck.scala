package security

import helpers._
import models._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * @author zepeng.li@gmail.com
 */
trait PermissionCheck
  extends BasicPlayComponents
  with DefaultPlayExecutor
  with Logging {

  def actions: Seq[CheckedActions => CheckedAction]

  def resource: CheckedResource

  def basicPlayApi: BasicPlayApi

  def _accessControls: AccessControls

  def checkOne[A](u: User, action: CheckedAction): Future[Option[Boolean]] = {
    for {
      b1 <- thenCheck(
        previous = None, action,
        ac => _accessControls.check(
          resource.name, ac.name, u.groups
        )
      )
      b2 <- thenCheck(
        previous = b1, action,
        ac => _accessControls.check(
          resource.name, ac.name, u.id
        )
      )
    } yield b2
  }

  def check[A](u: User): Future[Option[Boolean]] = {
    Future.sequence(
      actions.map(_ apply CheckedActions).map {checkOne(u, _)}
    ).map {
      _.reduce[Option[Boolean]] { (aOpt, bOpt) =>
        for (a <- aOpt; b <- bOpt) yield a && b
      }
    }
  }
  private def thenCheck[A](
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
        case b@Some(true)  => toOption(tryToCheck(action)).map(_.orElse(b))
      }
    } yield b2
  }

  private def toOption(f: => Future[AccessControl.Granted[_]]): Future[Option[Boolean]] =
    f.andThen {
      case Success(granted)                       => Logger.debug(granted.reason)
      case Failure(e: AccessControl.Denied[_])    => Logger.warn(e.reason)
      case Failure(e: AccessControl.Undefined[_]) => Logger.trace(e.reason)
    }.map {
      granted => Some(granted.canAccess)
    }.recover {
      case e: AccessControl.Denied[_]    => Some(false)
      case e: AccessControl.Undefined[_] => None
    }
}
