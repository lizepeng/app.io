package models.cassandra

import com.websudos.phantom.CassandraTable
import helpers.CanonicalNamed

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait EntityTable[R] extends Indexable[R] {
  self: CassandraTable[_, R] =>
}

trait Indexable[R] extends CanonicalNamed {

  def isEmpty: Future[Boolean]
}