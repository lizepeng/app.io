package filters

import akka.stream.Materializer
import com.mohiva.play.htmlcompressor.DefaultHTMLCompressorFilter
import play.api._

/**
 * @author zepeng.li@gmail.com
 */
class HtmlCompressor(
  configuration: Configuration,
  environment: Environment
)(implicit mat: Materializer)
  extends DefaultHTMLCompressorFilter(configuration, environment, mat)