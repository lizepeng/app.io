package protocols

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.cfs.Block._
import play.api.http._
import play.api.libs.MimeTypes
import play.api.mvc._
import play.utils.UriEncoding

/**
 * @author zepeng.li@gmail.com
 */

trait HttpDownloadable {

  def size: Long
  def name: String
  def whole: Source[BLK, NotUsed]
}

trait HttpDownloadResult extends HeaderNames with Status {

  def send(
    file: HttpDownloadable,
    name: HttpDownloadable => String = _.name,
    inline: Boolean = false
  ): Result = {
    val result = Result(
      ResponseHeader(OK),
      HttpEntity.Streamed(
        file.whole,
        Some(file.size),
        contentTypeOf(file)
      )
    )
    val encoded = UriEncoding.encodePathSegment(name(file), StandardCharsets.UTF_8)
    result.withHeaders(
      CONTENT_DISPOSITION -> {
        val dispositionType = if (inline) "inline" else "attachment"
        s"""$dispositionType; filename="$name"; filename*=utf-8''$encoded"""
      }
    )
  }

  def contentTypeOf(inode: HttpDownloadable): Option[String] = {
    MimeTypes.forFileName(inode.name).orElse(Some(ContentTypes.BINARY))
  }
}

object HttpDownloadResult extends HttpDownloadResult