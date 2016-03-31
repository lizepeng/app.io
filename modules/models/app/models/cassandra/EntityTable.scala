package models.cassandra

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.misc._
import play.api.libs.iteratee._

import scala.concurrent._
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
trait EntityTable[R] extends ESIndexable[R] {
  self: CassandraTable[_, R] =>
}

trait ESIndexable[R] extends CanonicalNamed with SortableFields[R] {

  def isEmpty: Future[Boolean]
}

trait SortableFields[R] {

  def sortable: Set[SortableField] = Set()

  implicit def columnToSortableField(
    column: Column[_, R, _]
  ): SortableField = SortableField(column.name)

  implicit def optionalColumnToSortableField(
    column: OptionalColumn[_, R, _]
  ): SortableField = SortableField(column.name)
}

case class SortableField(name: String) extends AnyVal

trait EntityCollect[R] {
  self: CassandraTable[_, R] =>

  def collect(predicate: R => Boolean): Collect = new Collect(predicate)

  class Collect(predicate: R => Boolean) {

    def >>:(page: Page[UUID]) = {
      (stream(page.elements, predicate) |>>> Iteratee.getChunks).map(Page(page.pager, _))
    }
  }

  def stream(ids: Traversable[UUID], predicate: R => Boolean): Enumerator[R]
}