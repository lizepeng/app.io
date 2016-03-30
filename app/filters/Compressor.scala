package filters

import akka.stream.Materializer
import play.api.http._
import play.api.mvc.{EssentialAction, EssentialFilter}
import play.filters.gzip.{GzipFilter, GzipFilterConfig}

/**
 * @author zepeng.li@gmail.com
 */
class Compressor(conf: GzipFilterConfig)(
  implicit mat: Materializer
) extends EssentialFilter {

  lazy val filter = new GzipFilter(
    conf.withShouldGzip {
      (req, resp) =>
        resp.header.headers.get(HeaderNames.CONTENT_TYPE).exists {
          case s if s.startsWith(MimeTypes.JSON) => true
          case s if s.startsWith(MimeTypes.HTML) => true
          case _                                 => false
        }
    }
  )(mat)

  def apply(next: EssentialAction): EssentialAction = filter.apply(next)
}