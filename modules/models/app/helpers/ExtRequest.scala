package helpers

import java.net.InetAddress

import play.api.http.HeaderNames
import play.api.mvc.RequestHeader

import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
object ExtRequest {

  implicit class RichRequest(val req: RequestHeader) extends AnyVal {

    // X-Forwarded-For: client, proxy1, proxy2
    def clientIP = Try {
      InetAddress.getByName(
        req.headers
          .get(HeaderNames.X_FORWARDED_FOR)
          .flatMap(_.split(",").headOption.map(_.trim))
          .getOrElse(req.remoteAddress)
      )
    }
  }
}