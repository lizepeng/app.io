package elasticsearch

import java.io.IOException

import helpers.{PageLike, Pager}
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.compress._
import org.elasticsearch.common.io.Streams
import org.elasticsearch.common.xcontent._
import play.api.http._
import play.api.mvc.Codec

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class ESMPage(pager: Pager, resp: MultiSearchResponse) extends PageLike {

  def hasNext: Boolean = true //resp.getHits.hits.size > pager.pageSize
}

object ESMPage {

  /**
   * `Writable` for `SearchHits` values - Json
   *
   * In order to test whether there exists next page,
   * we intend to fetch one more than we need.
   * Thus, we have to subtract 1 from total
   * and exclude the last hit in hits array.
   */
  implicit def writableOf_SearchHits(implicit codec: Codec): Writeable[ESMPage] = {

    Writeable(
      p => {
        //        val hits = p.resp.getHits
        //        val empty = ToXContent.EMPTY_PARAMS
        //
        //        val builder = XContentFactory.jsonBuilder.prettyPrint
        //        builder.startArray
        //
        //        val array = hits.hits()
        //        for (i <- 0 until Math.min(p.pager.pageSize, array.length)) {
        //          writeDirect(array(i).sourceRef, builder, empty)
        //        }
        //
        //        builder.endArray
        //        codec.encode(builder.string())
        codec.encode(p.resp.toString())
      }
    )
  }

  /**
   * Default content type for `SearchHits` values (`application/json`).
   */
  implicit def contentTypeOf_SearchHits(implicit codec: Codec): ContentTypeOf[ESMPage] =
    ContentTypeOf[ESMPage](Some(ContentTypes.JSON))

  /**
   * Directly writes the source to the output builder
   *
   * The only thing we're aiming for here is a raw pretty printed Json Array
   *
   * @see [[org.elasticsearch.common.xcontent.XContentHelper.writeDirect]]
   * @throws IOException
   */
  def writeDirect(
    source: BytesReference,
    builder: XContentBuilder,
    params: ToXContent.Params
  ) {
    val compressor = CompressorFactory.compressor(source)
    if (compressor != null) {
      val compressedStreamInput = compressor.streamInput(source.streamInput)
      val contentType = XContentFactory.xContentType(compressedStreamInput)
      compressedStreamInput.resetToBufferStart()
      if (contentType == builder.contentType) {
        Streams.copy(compressedStreamInput, builder.stream)
      }
      else {
        using(XContentFactory.xContent(contentType).createParser(compressedStreamInput)) {
          builder.copyCurrentStructure(_)
        }
      }
    }
    else {
      val contentType = XContentFactory.xContentType(source)
      using(XContentFactory.xContent(contentType).createParser(source)) {
        builder.copyCurrentStructure(_)
      }
    }
  }

  def using(parser: XContentParser)(f: XContentParser => Unit) = {
    try {
      parser.nextToken
      f(parser)
    } finally {
      if (parser != null) parser.close()
    }
  }
}