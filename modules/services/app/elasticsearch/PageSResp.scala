package elasticsearch

import helpers.{PageLike, Pager}
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.xcontent._
import play.api.http._
import play.api.mvc.Codec

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class PageSResp(pager: Pager, resp: SearchResponse) extends PageLike {

  def hasNext: Boolean = resp.getHits.hits.size > pager.pageSize
}

object PageSResp {

  /**
   * `Writable` for `PageSResp` values - Json
   *
   * In order to test whether there exists next page,
   * we intend to fetch one more than we need.
   * Thus, we have to subtract 1 from total
   * and exclude the last hit in hits array.
   */
  implicit def writableOf_PageSearchResponse(
    implicit codec: Codec
  ): Writeable[PageSResp] = {

    Writeable(
      p => {
        val hits = p.resp.getHits
        val empty = ToXContent.EMPTY_PARAMS

        val builder = XContentFactory.jsonBuilder.prettyPrint
        builder.startArray

        val array = hits.hits()
        for (i <- 0 until Math.min(p.pager.pageSize, array.length)) {
          writeDirect(array(i).sourceRef, builder, empty)
        }

        builder.endArray
        codec.encode(builder.string())
      }
    )
  }

  /**
   * Default content type for `PageSResp` values (`application/json`).
   */
  implicit def contentTypeOf_PageSearchResponse(
    implicit codec: Codec
  ): ContentTypeOf[PageSResp] =
    ContentTypeOf[PageSResp](Some(ContentTypes.JSON))

}