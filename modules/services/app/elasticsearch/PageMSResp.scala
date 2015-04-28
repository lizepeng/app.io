package elasticsearch

import helpers.{PageLike, Pager}
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.common.xcontent._
import play.api.http._
import play.api.mvc.Codec

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class PageMSResp(
  pager: Pager,
  resp: MultiSearchResponse
) extends PageLike {

  def hasNext: Boolean = true //resp.getHits.hits.size > pager.pageSize
}

object PageMSResp {

  /**
   * `Writable` for `PageMSResp` values - Json
   *
   * In order to test whether there exists next page,
   * we intend to fetch one more than we need.
   * Thus, we have to subtract 1 from total
   * and exclude the last hit in hits array.
   */
  implicit def writableOf_PageMultiSearchResponse(
    implicit codec: Codec
  ): Writeable[PageMSResp] = {

    Writeable(
      p => {
        val builder = XContentFactory.jsonBuilder.prettyPrint
        builder.startArray
        val empty = ToXContent.EMPTY_PARAMS
        var count = 0
        for (item <- p.resp.getResponses) {
          if (!item.isFailure) {
            val hits = item.getResponse.getHits

            val array = hits.hits()
            for (i <- 0 until Math.min(p.pager.pageSize - count, array.length)) {
              val hit = array(i)
              builder.startObject
              builder.field("_type", hit.getType)
              XContentHelper.writeRawField("_source", hit.sourceRef, builder, empty)
              builder.endObject
            }
            count += array.length
          }
        }

        builder.endArray
        codec.encode(builder.string())
      }
    )
  }

  /**
   * Default content type for `PageMSResp` values (`application/json`).
   */
  implicit def contentTypeOf_PageMultiSearchResponse(
    implicit codec: Codec
  ): ContentTypeOf[PageMSResp] =
    ContentTypeOf[PageMSResp](Some(ContentTypes.JSON))
}