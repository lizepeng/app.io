package helpers

import play.api.mvc.QueryStringBindable

import scala.collection.Iterable
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class Page[E](pager: Pager, elements: Iterable[E]) extends PageLike {

  def hasNext = elements.size > pager.pageSize

}

object Page {

  implicit def PageToIterator[E](p: Page[E]): Iterable[E] =
    p.elements.take(p.pager.pageSize)
}

trait PageLike {

  def pager: Pager

  def hasNext: Boolean

  def hasPrev: Boolean = pager.start > 0
}

case class Pager(start: Int, limit: Int) {

  val pageSize = limit - 1
  val pageNum  = start / pageSize + 1

  def prev = copy((start - pageSize) max 0, limit)

  def next = copy(start + pageSize, limit)
}

object Pager {

  def first = Pager(0, 15 + 1)

  implicit def queryStringBinder(
    implicit intBinder: QueryStringBindable[Int]
  ): QueryStringBindable[Pager] =
    new QueryStringBindable[Pager] {

      override def bind(
        key: String,
        params: Map[String, Seq[String]]
      ): Option[Either[String, Pager]] = {
        for {
          start <- intBinder.bind(s"page", params)
          limit <- intBinder.bind(s"per_page", params)
        } yield {
          (start, limit) match {
            case (Right(n), Right(s)) =>
              if (n > 0 && s > 0) Right(Pager((n - 1) * s, s + 1))
              else Left("query.params.page.lte.zero")
            case _                    => Left("query.params.page.failed")
          }
        }
      }

      override def unbind(key: String, p: Pager): String = {
        val start = intBinder.unbind(s"page", p.pageNum)
        val limit = intBinder.unbind(s"per_page", p.pageSize)
        s"$start&$limit"
      }
    }
}