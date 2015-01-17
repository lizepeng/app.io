package models.helpers

import play.api.mvc.QueryStringBindable

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class Page[E](pager: Pager, elements: Iterator[E])

object Page {

  implicit def PageToIterator[E](p: Page[E]): Iterator[E] = p.elements
}

case class Pager(start: Int, limit: Int) {

  def prev = copy((start - limit) max 0, limit)

  def next = copy(start + limit, limit)

  def hasPrev = start > 0
}

object Pager {
  def first = Pager(0, 20)

  implicit def queryStringBinder(
    implicit intBinder: QueryStringBindable[Int]
  ): QueryStringBindable[Pager] =
    new QueryStringBindable[Pager] {

      override def bind(
        key: String,
        params: Map[String, Seq[String]]
      ): Option[Either[String, Pager]] = {
        for {
          start <- intBinder.bind(s"$key.start", params)
          limit <- intBinder.bind(s"$key.limit", params)
        } yield {
          (start, limit) match {
            case (Right(s), Right(l)) => Right(Pager(s, l))
            case _                    => Left("Unable to bind a Pager")
          }
        }
      }

      override def unbind(key: String, p: Pager): String = {
        val start = intBinder.unbind(s"$key.start", p.start)
        val limit = intBinder.unbind(s"$key.limit", p.limit)
        s"$start&$limit"
      }
    }
}