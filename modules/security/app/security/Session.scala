package security

import models._
import play.api.http.HttpConfiguration
import play.api.libs.crypto.CookieSigner
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
  self =>

  def httpConfiguration: HttpConfiguration

  def cookieSigner: CookieSigner

  implicit def defaultContext: ExecutionContext

  object SessionCookieBaker extends CookieBaker[PlaySession] {

    def config = httpConfiguration.session
    def COOKIE_NAME = config.cookieName
    val emptyCookie = new PlaySession
    override val isSigned = true
    override def secure = config.secure
    override def maxAge = config.maxAge.map(_.toSeconds.toInt).orElse(Some(1.day.toSeconds.toInt))
    override def httpOnly = config.httpOnly
    override def path = httpConfiguration.context
    override def domain = config.domain
    def cookieSigner = self.cookieSigner
    def deserialize(data: Map[String, String]) = new PlaySession(data)
    def serialize(session: PlaySession) = session.data

    def encodeAsCookie(data: PlaySession)(rememberMe: Boolean): Cookie = {
      val cookie = encode(serialize(data))
      Cookie(COOKIE_NAME, cookie, if (rememberMe) maxAge else None, path, domain, secure, httpOnly)
    }
  }

  implicit class ResultWithSession(result: Result) {

    def createSession(user: User, rememberMe: Boolean)(
      implicit _users: Users
    ): Future[Result] = _users.saveSessionId(
      user.withNewSessionId,
      SessionCookieBaker.maxAge.get seconds
    ).map { u =>
      if (u.session_id.isEmpty) result.withNewSession
      else result.withNewSession.withCookies(
        SessionCookieBaker.encodeAsCookie(
          PlaySession.emptyCookie
            + (Session.USER_ID_KEY -> u.id.toString)
            + (Session.SESS_ID_KEY -> u.session_id.get)
        )(rememberMe)
      )
    }

    def destroySession(user: User)(
      implicit _users: Users
    ): Future[Result] = _users.saveSessionId(
      user.withNoSessionId,
      SessionCookieBaker.maxAge.get seconds
    ).map { u =>
      result.withNewSession
    }
  }
}