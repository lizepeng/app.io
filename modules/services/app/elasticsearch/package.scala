import com.sksamuel.elastic4s.source._
import models.misc._
import org.elasticsearch.action.search.SearchResponse
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
    Writeable(response => codec.encode(response.toString))
  }

  /**
   * Default content type for `SearchResponse` values (`application/json`).
   */
  implicit def contentTypeOf_SearchResponse(implicit codec: Codec): ContentTypeOf[SearchResponse] =
    ContentTypeOf[SearchResponse](Some(ContentTypes.JSON))

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