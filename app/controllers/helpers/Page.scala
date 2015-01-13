package controllers.helpers

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class Page[E](start: Int, limit: Int, elements: Iterator[E]) {
  def prev = (start - limit) max 0

  def next = start + limit

  def hasPrev = start > 0
}

object Page {
  implicit def PageToIterator[E](p: Page[E]): Iterator[E] = p.elements
}