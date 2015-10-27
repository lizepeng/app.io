package protocols

import play.api.http._
import play.api.libs.MimeTypes
import play.api.libs.iteratee.Enumerator
import play.api.mvc._
import play.utils.UriEncoding

/**
 * See RFC 7233
 *
 * @author zepeng.li@gmail.com
 */
case class HttpRangeRequest(resource: HttpRangeRequestable)(
  implicit req: RequestHeader
) extends HeaderNames with Status {

  def stream: Result = {
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
      case (first, lastOpt) if first < resource.size  => validRange(first, lastOpt)
      case (first, lastOpt) if first >= resource.size => invalidRange
    }.getOrElse {
      whole().withHeaders(ACCEPT_RANGES -> "bytes")
    }
  }

  def whole(name: Option[String] = None, inline: Boolean = false): Result = {
    val result = Result(
      ResponseHeader(
        OK,
        Map(
          CONTENT_TYPE -> contentTypeOf(resource),
          CONTENT_LENGTH -> s"${resource.size}"
        )
      ),
      resource.whole
    )
    if (inline) result
    else {
      val filename = name.getOrElse(UriEncoding.encodePathSegment(resource.name, "utf-8"))
      result.withHeaders(
        CONTENT_DISPOSITION ->
          s"""attachment; filename="$filename" """
      )
    }
  }


  private def validRange(first: Long, lastOpt: Option[Long]): Result = {
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

  private def invalidRange: Result = {
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

  private def contentTypeOf(inode: HttpRangeRequestable): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }
}

trait HttpRangeRequestable extends Any {
  def size: Long
  def name: String
  def range(first: Long, length: Long): Enumerator[Array[Byte]]
  def whole: Enumerator[Array[Byte]]
}