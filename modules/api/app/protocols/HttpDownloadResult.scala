package protocols

import play.api.http._
import play.api.libs.MimeTypes
import play.api.libs.iteratee.Enumerator
import play.api.mvc._
import play.utils.UriEncoding

/**
 * @author zepeng.li@gmail.com
 */

trait HttpDownloadable {
  def size: Long
  def name: String
  def whole: Enumerator[Array[Byte]]
}

trait HttpDownloadResult extends HeaderNames with Status {

  def send(
    file: HttpDownloadable,
    name: HttpDownloadable => String = _.name,
    inline: Boolean = false
  ): Result = {
    val result = Result(
      ResponseHeader(
        OK,
        Map(
          CONTENT_TYPE -> contentTypeOf(file),
          CONTENT_LENGTH -> s"${file.size}"
        )
      ),
      file.whole
    )
    if (inline) result
    else {
      val encoded = UriEncoding.encodePathSegment(name(file), "utf-8")
      result.withHeaders(
        CONTENT_DISPOSITION -> s"""attachment; filename="$encoded" """
      )
    }
  }

  private def contentTypeOf(inode: HttpDownloadable): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }
}

object HttpDownloadResult extends HttpDownloadResult