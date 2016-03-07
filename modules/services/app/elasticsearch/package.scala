import java.io.IOException

import com.sksamuel.elastic4s.source._
import models.misc._
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.compress.CompressorFactory
import org.elasticsearch.common.io.Streams
import org.elasticsearch.common.xcontent._
import play.api.http._
import play.api.libs.json._
import play.api.mvc._

import scala.collection.immutable.IndexedSeq
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object elasticsearch {

  case class JsonDocSource(jsval: JsValue) extends DocumentSource {

    val json = Json.stringify(jsval)
  }

  implicit def toJsonDocSource[T](o: T)(
    implicit writes: Writes[T]
  ): JsonDocSource = JsonDocSource(Json.toJson(o))

  /**
   * `Writable` for `SearchResponse` values - Json
   */
  implicit def writableOf_SearchResponse(implicit codec: Codec): Writeable[SearchResponse] = {
    import play.api.libs.iteratee.Execution.Implicits.trampoline
    Writeable(response => codec.encode(response.toString))
  }

  /**
   * Default content type for `SearchResponse` values (`application/json`).
   */
  implicit def contentTypeOf_SearchResponse(implicit codec: Codec): ContentTypeOf[SearchResponse] =
    ContentTypeOf[SearchResponse](Some(ContentTypes.JSON))

  /**
   * Directly writes the source to the output builder
   *
   * The only thing we're aiming for here is a raw pretty printed Json Array
   *
   * @see [[org.elasticsearch.common.xcontent.XContentHelper.writeDirect]]
   * @throws IOException
   */
  private[elasticsearch] def writeDirect(
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

  private def using(parser: XContentParser)(f: XContentParser => Unit) = {
    try {
      parser.nextToken
      f(parser)
    } finally {
      if (parser != null) parser.close()
    }
  }

  implicit class RichPager(val p: Pager) extends AnyVal {

    def /(n: Int): IndexedSeq[Pager] = {
      if (n == 0)
        IndexedSeq(p)
      else {
        val p1: Pager = p.copy(
          start = Math.max(p.start / n, 0),
          limit = Math.max(p.limit / n, 2)
        )
        val p2: Pager = p.copy(
          start = Math.max(p.start / n + p.start % n, 0),
          limit = Math.max(p.limit / n + p.limit % n, 2)
        )
        for (i <- 1 to n)
          yield if (i == n) p2 else p1.copy()
      }
    }
  }

  import com.websudos.phantom.dsl._

  implicit class ColumnToSortField(val column: Column[_, _, _]) extends AnyVal {

    def asc = new AscendingSortField(column.name)

    def desc = new DescendingSortField(column.name)
  }

  implicit class optionalColumnToSortField(val column: OptionalColumn[_, _, _]) extends AnyVal {

    def asc = new AscendingSortField(column.name)

    def desc = new DescendingSortField(column.name)
  }

  implicit class CassandraTableToSortFields[T](val table: T) extends AnyVal {
    def sorting(fs: (T => SortField)*): Seq[SortField] = fs.map(f => f(table))
  }

}