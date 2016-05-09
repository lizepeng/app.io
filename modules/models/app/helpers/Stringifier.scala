package helpers

import java.util.UUID

import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait Stringifier[T] {

  def << : String => Try[T]

  def <<(str: String, default: T): T = <<(str).getOrElse(default)

  def <<<(str: String): Option[T] = <<(str).toOption

  def >>: : T => String
}

object Stringifier {

  implicit val uuidStringifier = new Stringifier[UUID] {

    def << = str => Try(UUID.fromString(str))

    def >>: = _.toString
  }

  implicit val stringStringifier = new Stringifier[String] {

    def << = s => Success(s)

    def >>: = s => s
  }
}

object StringifierConverts {

  implicit class StringListToTypeList(val coll: List[String]) extends AnyVal {

    def elementToType[E](implicit sf: Stringifier[E]): List[E] = {
      coll.map(sf << _).collect { case Success(e) => e }
    }
  }

  implicit class TypeListToStringList[E](val coll: List[E]) extends AnyVal {

    def elementToString(implicit sf: Stringifier[E]): List[String] = {
      coll.map(_ >>: sf)
    }
  }

  implicit class StringSetToTypeSet(val coll: Set[String]) extends AnyVal {

    def elementToType[E](implicit sf: Stringifier[E]): Set[E] = {
      coll.map(sf << _).collect { case Success(e) => e }
    }
  }

  implicit class TypeSetToStringSet[E](val coll: Set[E]) extends AnyVal {

    def elementToString(implicit sf: Stringifier[E]): Set[String] = {
      coll.map(_ >>: sf)
    }
  }

  implicit class StringSeqToTypeSeq(val coll: Seq[String]) extends AnyVal {

    def elementToType[E](implicit sf: Stringifier[E]): Seq[E] = {
      coll.map(sf << _).collect { case Success(e) => e }
    }
  }

  implicit class TypeSeqToStringSeq[E](val coll: Seq[E]) extends AnyVal {

    def elementToString(implicit sf: Stringifier[E]): Seq[String] = {
      coll.map(_ >>: sf)
    }
  }

  implicit class StringKeyMapToTypeKeyMap[V](val map: Map[String, V]) extends AnyVal {

    def keyToType[K](implicit sf: Stringifier[K]): Map[K, V] = {
      map.map { case (k, v) => (sf << k) -> v }.collect { case (Success(k), v) => k -> v }
    }
  }

  implicit class TypeKeyMapToStringKeyMap[K, V](val map: Map[K, V]) extends AnyVal {

    def keyToString(implicit sf: Stringifier[K]): Map[String, V] = {
      map.map { case (k, v) => (k >>: sf) -> v }
    }
  }

  implicit class StringValueMapToTypeValueMap[K](val map: Map[K, String]) extends AnyVal {

    def valueToType[V](implicit sf: Stringifier[V]): Map[K, V] = {
      map.map { case (k, v) => k -> (sf << v) }.collect { case (k, Success(v)) => k -> v }
    }
  }

  implicit class TypeValueMapToStringValueMap[K, V](val map: Map[K, V]) extends AnyVal {

    def valueToString(implicit sf: Stringifier[V]): Map[K, String] = {
      map.map { case (k, v) => k -> (v >>: sf) }
    }
  }
}