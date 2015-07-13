package helpers

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object ExtLang {

  object Lang

  implicit def wrappedLang(lang: Lang.type): play.api.i18n.Lang.type = play.api.i18n.Lang

  type Lang = play.api.i18n.Lang

  implicit val langReads: Reads[Lang] = new Reads[Lang] {
    def reads(json: JsValue) =
      Reads.StringReads.reads(json).flatMap { s =>
        play.api.i18n.Lang.get(s) match {
          case Some(l) => JsSuccess(l)
          case None    => JsError(ValidationError("error.lang.wrong.code", s))
        }
      }
  }

  implicit val langWrites: Writes[Lang] = new Writes[Lang] {
    def writes(o: Lang): JsValue = JsString(o.code)
  }
}