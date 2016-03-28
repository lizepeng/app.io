package helpers

import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object EnumLike {

  trait Value extends Any {

    def self: String

    override def toString = self

    def in(others: Any*) = others.map(_ == this).reduce(_ || _)
  }

  trait Definition[T <: Value] extends CanonicalNamed {

    val namePattern = """(\w+)\$""".r

    val basicName = namePattern.findAllIn(this.getClass.getName).map(_.dropRight(1)).mkString(".")

    def values: Seq[T]

    def Unknown: T

    def withNameOpt(name: String): Option[T] = values.find(_.self == name)

    def withName(name: String): T = withNameOpt(name).getOrElse(Unknown)

    def hasName(name: String): Boolean = values.exists(_.self == name)

    def msg(v: Value, postfix: String = "")(implicit messages: Messages) = {
      messages(s"$basicName.$v" + (if (postfix.isEmpty) postfix else s".$postfix"))
    }

    def toJson(postfix: String = "")(implicit messages: Messages) = Json.prettyPrint(
      JsObject(
        values.map { v =>
          v.self -> JsString(msg(v, postfix))
        }
      )
    )

    implicit def self: EnumLike.Definition[T]

    import EnumLikeMapConverts._

    implicit def jsonMapFormat[A](implicit fmt: Format[A]): Format[Map[T, A]] = {
      Format.of[Map[String, A]].inmap[Map[T, A]](_.keyToEnum[T], _.keyToString)
    }
  }
}


object EnumLikeListConverts {

  implicit class StringListToEnumLikeList(val coll: List[String]) extends AnyVal {

    def elementToEnum[E <: EnumLike.Value](implicit enum: EnumLike.Definition[E]): List[E] = {
      coll.collect { case s if enum.hasName(s) => enum.withName(s) }
    }
  }

  implicit class EnumLikeListToStringList[E <: EnumLike.Value](val coll: List[E]) extends AnyVal {

    def elementToString: List[String] = {
      coll.map { case s => s.self }
    }
  }
}

object EnumLikeMapConverts {

  implicit class StringKeyMapToEnumLikeKeyMap[A](val map: Map[String, A]) extends AnyVal {

    def keyToEnum[K <: EnumLike.Value](implicit enum: EnumLike.Definition[K]): Map[K, A] = {
      map.collect { case (k, v) if enum.hasName(k) => enum.withName(k) -> v }
    }
  }

  implicit class EnumLikeKeyMapToStringKeyMap[K <: EnumLike.Value, A](val map: Map[K, A]) extends AnyVal {

    def keyToString: Map[String, A] = {
      map.map { case (k, v) => k.self -> v }
    }
  }

  implicit class EnumLikeKeyMapEnumLikeKeyMap[K <: EnumLike.Value, A](val map: Map[K, A]) extends AnyVal {

    def keyToEnum[J <: EnumLike.Value](implicit enum: EnumLike.Definition[J]): Map[J, A] = {
      map.map { case (k, v) if enum.hasName(k.self) => enum.withName(k.self) -> v }
    }
  }

  implicit class StringValueMapToEnumLikeValueMap[A](val map: Map[A, String]) extends AnyVal {

    def valueToEnum[V <: EnumLike.Value](implicit enum: EnumLike.Definition[V]): Map[A, V] = {
      map.collect { case (k, v) if enum.hasName(v) => k -> enum.withName(v) }
    }
  }

  implicit class EnumLikeValueMapToStringValueMap[V <: EnumLike.Value, A](val map: Map[A, V]) extends AnyVal {

    def valueToString: Map[A, String] = {
      map.map { case (k, v) => k -> v.self }
    }
  }

  implicit class StringMapToEnumLikeMap(val map: Map[String, String]) extends AnyVal {

    def toEnumMap[K <: EnumLike.Value, V <: EnumLike.Value](
      implicit enumK: EnumLike.Definition[K], enumV: EnumLike.Definition[V]
    ): Map[K, V] = map.keyToEnum[K].valueToEnum[V]
  }

  implicit class EnumLikeMapToStringMap[K <: EnumLike.Value, V <: EnumLike.Value](val map: Map[K, V]) extends AnyVal {

    def toStringMap: Map[String, String] = map.keyToString.valueToString
  }
}