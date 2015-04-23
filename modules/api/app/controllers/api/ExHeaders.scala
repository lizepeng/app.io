package controllers.api

import helpers._
import play.api.Play.current
import play.api.mvc.Call

/**
 * @author zepeng.li@gmail.com
 */
trait ExHeaders {
  self: AppConfig =>

  val LINK = "Link"

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
    page: Page[_], call: Pager => Call
  ): (String, String) = {
    //TODO how about the first or last page situation ?

    val next: Seq[String] = if (!page.hasNext) Seq[String]()
    else Seq( s"""<$hostname${call(page.pager.next)}>; rel="next"""")

    val prev: Seq[String] = if (!page.hasPrev) Seq()
    else Seq( s"""<$hostname${call(page.pager.prev)}>; rel="prev"""")

    LINK -> (next ++ prev).mkString(",")
  }
}