package helpers

import play.api.libs.json._

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
}