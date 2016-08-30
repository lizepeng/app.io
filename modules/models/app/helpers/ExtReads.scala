package helpers

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
object ExtReads {

  def always[T](default: => T) = new Reads[T] {
    def reads(json: JsValue) = JsSuccess(default)
  }

  implicit class RichJsPath(val path: JsPath) extends AnyVal {

    def formatNullable1[T](
      implicit f: Format[T]
    ): OFormat[Option[T]] = OFormat(
      Reads.nullable(path)(f),
      Writes.at(path)(Writes.optionWithNull[T](f))
    )

    def formatNullable1[T](r: Reads[T])(
      implicit w: Writes[T]
    ): OFormat[Option[T]] = OFormat(
      Reads.nullable(path)(r),
      Writes.at(path)(Writes.optionWithNull[T](w))
    )

    def formatNullable1[T](w: Writes[T])(
      implicit r: Reads[T]
    ): OFormat[Option[T]] = OFormat(
      Reads.nullable(path)(r),
      Writes.at(path)(Writes.optionWithNull[T](w))
    )

    def formatNullable2[T, T1](path1: JsPath, convert: T1 => T)(
      implicit fmtT: Format[T], fmtT1: Format[T1]
    ): OFormat[Option[T]] = OFormat(
      Reads.nullable(path)(fmtT).flatMap[Option[T]] {
        case None    => Reads.nullable(path1)(fmtT1).map(_.map(convert))
        case Some(t) => Reads.at(path)(Reads.pure(Some(t)))
      },
      Writes.at(path)(Writes.optionWithNull[T](fmtT))
    )
  }

  implicit def optionalFormat[T](implicit jsFmt: Format[T]): Format[Option[T]] =
    new Format[Option[T]] {
      override def reads(json: JsValue): JsResult[Option[T]] = json match {
        case JsNull => JsSuccess(None)
        case js     => jsFmt.reads(js).map(Some(_))
      }
      override def writes(o: Option[T]): JsValue = o match {
        case None    => JsNull
        case Some(t) => jsFmt.writes(t)
      }
    }

  implicit def uuidMapFormat[T](implicit jsFmt: Format[T]): Format[Map[UUID, T]] = {
    Format.of[Map[String, T]].inmap[Map[UUID, T]](
      _.map { case (k, v) =>
        Try(UUID.fromString(k)) -> v
      }.collect { case (k, v) if k.isSuccess =>
        k.get -> v
      }, _.map { case (k, v) =>
        k.toString -> v
      }
    )
  }

  trait NamedJsObjectFormat[T, S <: T] extends CanonicalNamed {

    def jsonFormat: Format[S]

    def writesJsObject: PartialFunction[(String, T), JsObject] = {
      case (s, obj) if s == basicName =>
        Json.obj(
          "type" -> basicName,
          "data" -> Json.toJson[S](obj.asInstanceOf[S])(jsonFormat)
        )
    }

    def readJsObject: PartialFunction[(String, JsValue), JsResult[T]] = {
      case (s, json) if s == basicName =>
        (__ \ "data").read[S](jsonFormat).reads(json)
    }
  }

  object NamedJsObjectFormat {

    def jsonFormat[T <: CanonicalNamed](formats: NamedJsObjectFormat[T, _]*) =
      Format[T](
        Reads { json =>
          (__ \ "type").read[String].reads(json).flatMap { tpe =>
            formats.map(_.readJsObject).reduce(_ orElse _).apply(tpe, json)
          }
        }, Writes { obj =>
          formats.map(_.writesJsObject).reduce(_ orElse _).apply((obj.basicName, obj))
        }
      )
  }
}