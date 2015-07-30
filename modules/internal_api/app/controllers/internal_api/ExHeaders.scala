package controllers.internal_api

import helpers._
import play.api.mvc.Call

/**
 * @author zepeng.li@gmail.com
 */
trait LinkHeader extends AppConfig with ExHeaders {
  self: CanonicalNamed with BasicPlayComponents =>

  /**
   * Create link header for pagination.
   *
   * first and last are not supported yet.
   *
   * @param page current page
   * @param call the function to supply link string
   * @return
   */
  def linkHeader(
    page: PageLike, call: Pager => Call
  ): (String, String) = {
    //TODO how about the first or last page situation ?

    val next: Seq[String] = if (!page.hasNext) Seq[String]()
    else Seq( s"""<$hostname${call(page.pager.next)}>; rel="next"""")

    val prev: Seq[String] = if (!page.hasPrev) Seq()
    else Seq( s"""<$hostname${call(page.pager.prev)}>; rel="prev"""")

    LINK -> (next ++ prev).mkString(",")
  }
}

trait ExHeaders {

  val LINK                   = "Link"
  val X_RATE_LIMIT_LIMIT     = "X-Rate-Limit-Limit"
  val X_RATE_LIMIT_REMAINING = "X-Rate-Limit-Remaining"
  val X_RATE_LIMIT_RESET     = "X-Rate-Limit-Reset"
}