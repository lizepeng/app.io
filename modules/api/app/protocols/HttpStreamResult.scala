package protocols

import play.api.http._
import play.api.libs.MimeTypes
import play.api.libs.iteratee.Enumerator
import play.api.mvc._

/**
 * See RFC 7233
 *
 * @author zepeng.li@gmail.com
 */
trait HttpStreamable extends HttpDownloadable {
  def range(first: Long, length: Long): Enumerator[Array[Byte]]
}

object HttpStreamResult extends HttpDownloadResult {

  def stream(resource: HttpStreamable)(
    implicit req: RequestHeader
  ): Result = {
    val byte_range_spec = """bytes=(\d+)-(\d*)""".r

    req.headers.get(RANGE).flatMap[(Long, Option[Long])] {
      case byte_range_spec(first, last) =>
        val byteRange: Some[(Long, Option[Long])] = Some(
          first.toLong,
          Some(last).filter(_.nonEmpty).map(_.toLong)
        )
        byteRange
      case _                            => None
    }.filter {
      case (first, lastOpt) => lastOpt.isEmpty || lastOpt.get >= first
    }.map {
      case (first, lastOpt) if first < resource.size  =>
        validRange(resource, first, lastOpt)
      case (first, lastOpt) if first >= resource.size =>
        invalidRange(resource)
    }.getOrElse {
      send(resource, inline = true).withHeaders(ACCEPT_RANGES -> "bytes")
    }
  }

  private def validRange(
    resource: HttpStreamable,
    first: Long,
    lastOpt: Option[Long]
  ): Result = {
    val end = lastOpt.filter(_ < resource.size).getOrElse(resource.size - 1)
    Result(
      ResponseHeader(
        PARTIAL_CONTENT,
        Map(
          ACCEPT_RANGES -> "bytes",
          CONTENT_TYPE -> contentTypeOf(resource),
          CONTENT_RANGE -> s"bytes $first-$end/${resource.size}",
          CONTENT_LENGTH -> s"${end - first + 1}"
        )
      ),
      resource.range(first, end - first + 1)
    )
  }

  private def invalidRange(resource: HttpStreamable): Result = {
    Result(
      ResponseHeader(
        REQUESTED_RANGE_NOT_SATISFIABLE,
        Map(
          CONTENT_RANGE -> s"*/${resource.size}",
          CONTENT_TYPE -> contentTypeOf(resource)
        )
      ),
      Enumerator.empty
    )
  }

  private def contentTypeOf(inode: HttpStreamable): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }
}