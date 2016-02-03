package models.misc

import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import play.api.http._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._

import scala.collection.Iterable
import scala.concurrent._
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
/**
 * A class presents a subset of elements in a data set.
 * In order to know whether next page exists, elements may be filled with 1 more elements.
 * One should use {{{Page.PageToIterable}}} to iterate elements in one page,
 * because which will exclude the redundant on from the iterating process.
 *
 * @param pager    the parameter indicates that current location in the whole data set
 * @param elements retrieved subset of the whole data set
 * @tparam E type of the element
 */
case class Page[E](pager: Pager, elements: Iterable[E]) extends PageLike {

  def hasNext = elements.size > pager.pageSize
}

object Page {

  /**
   * Helper for excluding the redundant element from iterating process.
   */
  implicit def PageToIterable[E](p: Page[E]): Iterable[E] =
    p.elements.take(p.pager.pageSize)

  implicit def writableOf_Page[E](
    implicit codec: Codec, tjs: Writes[E]
  ): Writeable[Page[E]] = {
    import play.api.libs.iteratee.Execution.Implicits.trampoline
    Writeable(
      p => codec.encode(Json.prettyPrint(Json.toJson(p: Iterable[E]))),
      Some(ContentTypes.JSON)
    )
  }

  def apply[R](p: Pager)(enumerator: Enumerator[R])(
    implicit ec: ExecutionContext
  ): Future[Page[R]] = {
    (enumerator |>>> PIteratee.slice[R](p.start, p.limit))
      .map(_.toIterable)
      .recover { case e: Throwable => Nil }
      .map(Page(p, _))
  }
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