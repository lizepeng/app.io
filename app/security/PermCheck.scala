package security

import controllers._
import helpers.{BaseException, Logging}
import models.AccessControl
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
object PermCheck extends Logging {

  def apply(
    resource: String,
    onDenied: RequestHeader => Result
  ): ActionFunction[UserRequest, UserRequest] = {
    apply(resource, "*", onDenied)
  }

  def apply(
    resource: String,
    action: String,
    onDenied: RequestHeader => Result
  ): ActionFunction[UserRequest, UserRequest] =
    AuthCheck andThen new ActionFilter[UserRequest] {

      override protected def filter[A](
        req: UserRequest[A]
      ): Future[Option[Result]] = {
        val u = req.user.get

        val checked = for {
          b1 <- check(
            previous = None, action,
            ac => AccessControl.find(resource, ac, u.id)
          )
          b2 <- check(
            previous = b1, action,
            ac => AccessControl.find(resource, ac, u.internal_groups)
          )
          b3 <- check(
            previous = b2, action,
            ac => AccessControl.find(resource, ac, u.external_groups)
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
    action: String,
    tryToCheck: String => Future[Option[Boolean]]
  ): Future[Option[Boolean]] = {
    if (previous.isDefined) Future.successful(previous)
    else for {
      b1 <- trace(tryToCheck(action))
      b2 <-
      if (b1.isEmpty && action != "*") trace(tryToCheck("*"))
      else Future.successful(b1)
    } yield b2
  }

  private def trace[A](f: => Future[A]): Future[A] = f.andThen {
    case Failure(e: BaseException) => Logger.trace(e.reason)
  }
}