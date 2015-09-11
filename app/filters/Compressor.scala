package filters

import play.api.http._
import play.filters.gzip.GzipFilter

/**
 * @author zepeng.li@gmail.com
 */
class Compressor extends GzipFilter(
  shouldGzip = (req, resp) =>
    resp.headers.get(HeaderNames.CONTENT_TYPE).exists {
      case s if s.startsWith(MimeTypes.JSON) => true
      case s if s.startsWith(MimeTypes.HTML) => true
      case _                                 => false
    }
)