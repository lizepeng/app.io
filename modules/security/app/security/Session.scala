package security

import models._
import play.api.Configuration
import play.api.mvc.{Session => PlaySession, _}

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object Session {

  val USER_ID_KEY = "USER_ID"
  val SESS_ID_KEY = "SESS_ID"
}

trait Session {

  def configuration: Configuration

  implicit def defaultContext: ExecutionContext

  lazy val maxAge = configuration
    .getMilliseconds("session.maxAge")
    .map(_ / 1000)
    .map(_.toInt)
    .getOrElse(1.day.toSeconds.toInt)

  class SessionCookieBaker(override val maxAge: Option[Int] = None) extends CookieBaker[PlaySession] {

    def COOKIE_NAME = PlaySession.COOKIE_NAME
    def emptyCookie = PlaySession.emptyCookie
    override val isSigned = true
    override def secure = PlaySession.secure
    override def httpOnly = PlaySession.httpOnly
    override def path = PlaySession.path
    override def domain = PlaySession.domain
    def deserialize(data: Map[String, String]) = PlaySession.deserialize(data)
    def serialize(session: PlaySession) = PlaySession.serialize(session)
  }

  implicit class ResultWithSession(result: Result) {

    def createSession(rememberMe: Boolean)(
      implicit user: User, _users: Users
    ): Future[Result] = {

      val cookieBaker = new SessionCookieBaker(if (rememberMe) Some(maxAge) else None)

      _users.saveSessionId(user.withNewSessionId, maxAge seconds).map { u =>

        if (u.session_id.isEmpty) result.withNewSession
        else result.withNewSession.withCookies(
          cookieBaker.encodeAsCookie(
            PlaySession.emptyCookie
              + (Session.USER_ID_KEY -> u.id.toString)
              + (Session.SESS_ID_KEY -> u.session_id.get)
          )
        )
      }
    }

    def destroySession: Result = result.withNewSession
  }
}