package helpers

import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object ExtReads {

  def always[T](default: => T) = new Reads[T] {
    def reads(json: JsValue) = JsSuccess(default)
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