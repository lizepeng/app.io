package models.misc

import java.util.Locale

import play.api.data.validation.ValidationError
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class CountryCode(code: String) extends AnyVal

object CountryCode {

  /**
   * Returns a set of all 2-letter country codes defined in ISO 3166.
   */
  val codes = Locale.getISOCountries.toSet

  val China        = CountryCode("CN")
  val Japan        = CountryCode("JP")
  val UnitedStates = CountryCode("US")

  implicit val jsonReads = new Reads[CountryCode] {
    def reads(json: JsValue): JsResult[CountryCode] =
      Reads.StringReads.reads(json).flatMap { code =>
        if (CountryCode.codes.contains(code)) JsSuccess(CountryCode(code))
        else JsError(ValidationError("error.expected.countrycode", code))
      }
  }

  implicit val jsonWrites: Writes[CountryCode] = new Writes[CountryCode] {
    def writes(country: CountryCode): JsValue = JsString(country.code)
  }

  implicit val jsonFormat: Format[CountryCode] = Format(jsonReads, jsonWrites)
}