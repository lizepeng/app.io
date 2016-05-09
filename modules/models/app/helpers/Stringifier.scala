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

object StringifierMapConverts {

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