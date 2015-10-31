package models.cassandra

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import helpers.CanonicalNamed

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
trait EntityTable[R] extends Indexable[R] {
  self: CassandraTable[_, R] =>
}

trait Indexable[R] extends CanonicalNamed with SortableFields[R] {

  def isEmpty: Future[Boolean]
}

trait SortableFields[R] {

  def sortable: Set[SortableField] = Set()

  implicit def columnToSortableField(column: Column[_, R, _]): SortableField =
    new SortableField(column.name)
}

class SortableField(val name: String) extends AnyVal