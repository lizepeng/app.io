package models.cassandra

import com.datastax.driver.core.Session
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.builder.primitives.Primitive
import com.websudos.phantom.builder.query.CQLQuery
import com.websudos.phantom.builder.syntax.CQLSyntax
import com.websudos.phantom.column._
import com.websudos.phantom.connectors._

/**
 * @author zepeng.li@gmail.com
 */
trait CassandraComponents {

  implicit val keySpace: KeySpace = KeySpace("app")

  def contactPoint: KeySpaceBuilder

  implicit lazy val session: Session = contactPoint.keySpace(keySpace.name).session
}

// Workaround since phantom 1.8.12 do not support static collection column?
class StaticMapColumn[Owner <: CassandraTable[Owner, Record], Record, K: Primitive, V: Primitive](
  table: CassandraTable[Owner, Record], isStatic: Boolean = true
)
  extends MapColumn[Owner, Record, K, V](table) with
  PrimitiveCollectionValue[V] {

  override def qb: CQLQuery = {
    val root = CQLQuery(name).forcePad.append(cassandraType)
    if (isStatic) {
      root.forcePad.append(CQLSyntax.static)
    } else {
      root
    }
  }
}