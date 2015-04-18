package controllers

import helpers.{AppConfig, Pager}
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
   * @param pager current pager
   * @param call the function to supply link string
   * @return
   */
  def linkHeader(
    pager: Pager, call: Pager => Call
  ): (String, String) = {
    LINK ->
      s"""<$hostname${call(pager.next)}>; rel="next",
         |<$hostname${call(pager.prev)}>; rel="prev"
       """
        .stripMargin
        .replaceAll("\n", "")
  }
}