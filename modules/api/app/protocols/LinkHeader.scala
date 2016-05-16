package protocols

import models.misc._
import play.api.mvc.{Call, RequestHeader}

/**
 * @author zepeng.li@gmail.com
 */
trait LinkHeader extends ExtHeaders {

  /**
   * Create link header for pagination.
   *
   * first and last are not supported yet.
   *
   * @param page current page
   * @param call the function to supply link string
   * @return
   */
  def linkHeader(page: PageLike, call: Pager => Call)(
    implicit req: RequestHeader
  ): (String, String) = {
    //TODO how about the first or last page situation ?

    val next: Seq[String] = if (!page.hasNext) Seq[String]()
    else Seq( s"""<${req.host}${call(page.pager.next)}>; rel="next"""")

    val prev: Seq[String] = if (!page.hasPrev) Seq()
    else Seq( s"""<${req.host}${call(page.pager.prev)}>; rel="prev"""")

    LINK -> (next ++ prev).mkString(",")
  }
}