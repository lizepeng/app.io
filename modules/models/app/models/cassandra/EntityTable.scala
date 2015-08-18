package models.cassandra

import com.websudos.phantom.CassandraTable
import helpers.CanonicalNamed

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait EntityTable[R] extends Indexable[R] {
  self: CassandraTable[_, R] =>

  def isEmpty: Future[Boolean]
}

trait Indexable[R] extends CanonicalNamed