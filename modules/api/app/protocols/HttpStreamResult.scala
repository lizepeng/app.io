package protocols

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.cfs.Block._
import play.api.http._
import play.api.mvc._

/**
 * See RFC 7233
 *
 * @author zepeng.li@gmail.com
 */
trait HttpStreamable extends HttpDownloadable {

  def range(first: Long, length: Long): Source[BLK, NotUsed]
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
          CONTENT_RANGE -> s"bytes $first-$end/${resource.size}"
        )
      ),
      HttpEntity.Streamed(
        resource.range(first, end - first + 1),
        Some(end - first + 1),
        contentTypeOf(resource)
      )
    )
  }

  private def invalidRange(resource: HttpStreamable): Result = {
    Result(
      ResponseHeader(
        REQUESTED_RANGE_NOT_SATISFIABLE,
        Map(
          CONTENT_RANGE -> s"*/${resource.size}"
        )
      ),
      HttpEntity.Streamed(
        Source.empty,
        None,
        contentTypeOf(resource)
      )
    )
  }
}