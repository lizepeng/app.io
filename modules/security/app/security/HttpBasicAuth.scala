package security

import play.api.http.HeaderNames
import play.api.mvc._
import sun.misc.BASE64Decoder

/**
 * @author zepeng.li@gmail.com
 */
object HttpBasicAuth {

  def apply(req: RequestHeader): Option[(String, String)] = {
    req.headers.get(HeaderNames.AUTHORIZATION).flatMap(decode)
  }

  /**
   * Decode username & password in [[HeaderNames.AUTHORIZATION]] header.
   *
   * @param auth string value in Header [[HeaderNames.AUTHORIZATION]]
   * @return pair consists of username & password
   */
  def decode(auth: String): Option[(String, String)] = {
    if (auth == null) return None
    val baStr = auth.replaceFirst("Basic ", "")
    val decoder = new BASE64Decoder()
    new String(decoder.decodeBuffer(baStr), "UTF-8").split(":") match {
      case Array(username, password) => Some(username, password)
      case _                         => None
    }
  }

  def onUnauthorized(realm: String): Result = {
    Results.Unauthorized.withHeaders(
      HeaderNames.WWW_AUTHENTICATE -> s"""Basic realm="$realm""""
    )
  }
}