package security

import helpers._
import models._
import play.api.mvc._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
trait Authentication {
  self: DefaultPlayExecutor =>

  def _users: Users

  def pam: PAM
}

trait PAM extends (Users => RequestHeader => Future[User]) {
  self =>

  def thenTry(that: PAM)(implicit ec: ExecutionContext): PAM = new PAM {
    override def apply(v1: Users): (RequestHeader) => Future[User] = {
      req => self.apply(v1)(req).recoverWith {
        case _: BaseException => that.apply(v1)(req)
      }
    }
  }

  def >>(that: PAM)(implicit ec: ExecutionContext) = thenTry(that)
}