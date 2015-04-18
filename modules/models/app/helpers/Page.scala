package helpers

import play.api.mvc.QueryStringBindable

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class Page[E](pager: Pager, elements: List[E]) {

  def hasNext = elements.size > pager.limit - 1

  def hasPrev = pager.start > 0
}

object Page {

  implicit def PageToIterator[E](p: Page[E]): List[E] =
    p.elements.take(p.pager.limit - 1)
}

case class Pager(start: Int, limit: Int) {

  def prev = copy((start - (limit - 1)) max 0, limit)

  def next = copy(start + (limit - 1), limit)
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
            case (Right(s), Right(l)) => Right(Pager(s * l, l + 1))
            case _                    => Left("Unable to bind a Pager")
          }
        }
      }

      override def unbind(key: String, p: Pager): String = {
        val start = intBinder.unbind(s"page", p.start / (p.limit - 1))
        val limit = intBinder.unbind(s"per_page", p.limit - 1)
        s"$start&$limit"
      }
    }
}