package models.cassandra

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.builder.primitives.Primitive
import com.websudos.phantom.builder.query.CQLQuery
import com.websudos.phantom.builder.syntax.CQLSyntax
import com.websudos.phantom.column._
import com.websudos.phantom.connectors._

/**
 * @author zepeng.li@gmail.com
 */
trait CassandraComponents extends RootConnector {

  def keySpaceDef: KeySpaceDef

  implicit def space = KeySpace(keySpaceDef.name)

  implicit def session = keySpaceDef.session
}

// Workaround since phantom 1.8.12 do not support static collection column?
class StaticMapColumn[Owner <: CassandraTable[Owner, Record], Record, K: Primitive, V: Primitive](
  table: CassandraTable[Owner, Record], isStatic: Boolean = true
)
  extends MapColumn[Owner, Record, K, V](table) with
    PrimitiveCollectionValue[V] {

  override def qb: CQLQuery = {
    val root = CQLQuery(name).forcePad.append(cassandraType)
    root.forcePad.append(CQLSyntax.static)
  }
}

class StaticListColumn[Owner <: CassandraTable[Owner, Record], Record, RR: Primitive](
  table: CassandraTable[Owner, Record], isStatic: Boolean = true
)
  extends ListColumn[Owner, Record, RR](table) with
    PrimitiveCollectionValue[RR] {

  override def qb: CQLQuery = {
    val root = CQLQuery(name).forcePad.append(cassandraType)
    root.forcePad.append(CQLSyntax.static)
  }
}

class StaticSetColumn[Owner <: CassandraTable[Owner, Record], Record, RR: Primitive](
  table: CassandraTable[Owner, Record], isStatic: Boolean = true
)
  extends SetColumn[Owner, Record, RR](table) with
    PrimitiveCollectionValue[RR] {

  override def qb: CQLQuery = {
    val root = CQLQuery(name).forcePad.append(cassandraType)
    root.forcePad.append(CQLSyntax.static)
  }
}

abstract class StaticJsonColumn[T <: CassandraTable[T, R], R, ValueType](
  table: CassandraTable[T, R], isStatic: Boolean = true
)
  extends JsonColumn[T, R, ValueType](table) {

  override def qb: CQLQuery = {
    val root = CQLQuery(name).forcePad.append(cassandraType)
    root.forcePad.append(CQLSyntax.static)
  }
}

abstract class StaticOptionalEnumColumn[Owner <: CassandraTable[Owner, Record], Record, EnumType <: Enumeration](
  table: CassandraTable[Owner, Record], enum: EnumType
)
  extends OptionalEnumColumn[Owner, Record, EnumType](table, enum) {

  override def qb: CQLQuery = {
    val root = CQLQuery(name).forcePad.append(cassandraType)
    root.forcePad.append(CQLSyntax.static)
  }
}