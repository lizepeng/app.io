package models.misc

import java.util.Locale

import play.api.data.validation.ValidationError
import play.api.i18n._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class CountryCode(code: String) extends AnyVal

object CountryCode {

  /**
   * Returns a set of all 2-letter country codes defined in ISO 3166.
   */
  val codes = Locale.getISOCountries.toSeq

  val values = codes.map(CountryCode.apply)

  def msg(v: CountryCode, prefix: Option[String] = None)(
    implicit messages: Messages
  ) = {
    messages(prefix.map(_ + ".").getOrElse("") + v.code)
  }

  def toJson(
    filter: Seq[CountryCode] => Seq[CountryCode] =
    _.sortBy { c =>
      val idx = CountryCode.familiar.indexOf(c)
      if (idx == -1) 9999 else idx
    },
    prefix: Option[String] = None
  )(
    implicit messages: Messages
  ) = Json.prettyPrint(
    JsObject(
      filter(values).map { v =>
        v.code -> JsString(msg(v, prefix))
      }
    )
  )

  val China         = CountryCode("CN")
  val UnitedStates  = CountryCode("US")
  val Cyprus        = CountryCode("CY")
  val Denmark       = CountryCode("DK")
  val UnitedKingdom = CountryCode("GB")
  val Greece        = CountryCode("GR")
  val Netherlands   = CountryCode("NL")
  val Ireland       = CountryCode("IE")
  val Malaysia      = CountryCode("MY")
  val Norway        = CountryCode("NO")
  val SouthAfrica   = CountryCode("ZA")
  val Singapore     = CountryCode("SG")
  val Australia     = CountryCode("AU")
  val Germany       = CountryCode("DE")
  val France        = CountryCode("FR")
  val Finland       = CountryCode("FI")
  val SouthKorea    = CountryCode("KR")
  val Canada        = CountryCode("CA")
  val Japan         = CountryCode("JP")
  val Sweden        = CountryCode("SE")
  val Switzerland   = CountryCode("CH")
  val NewZealand    = CountryCode("NZ")
  val Italy         = CountryCode("IT")
  val Poland        = CountryCode("PL")
  val Ukraine       = CountryCode("UA")
  val Russia        = CountryCode("RU")
  val Egypt         = CountryCode("EG")
  val Philippines   = CountryCode("PH")
  val Austria       = CountryCode("AT")
  val Thailand      = CountryCode("TH")
  val Bulgaria      = CountryCode("BG")
  val Belgium       = CountryCode("BE")
  val Spain         = CountryCode("ES")
  val Hungary       = CountryCode("HU")
  val Cuba          = CountryCode("CU")
  val Portugal      = CountryCode("PT")
  val Romania       = CountryCode("RO")
  val Cameroon      = CountryCode("CM")
  val Algeria       = CountryCode("DZ")
  val Belarus       = CountryCode("BY")
  val Mauritius     = CountryCode("MU")
  val SriLanka      = CountryCode("LK")
  val Kyrgyzstan    = CountryCode("KG")
  val Latvia        = CountryCode("LV")
  val Liechtenstein = CountryCode("LI")
  val Israel        = CountryCode("IL")
  val Luxembourg    = CountryCode("LU")
  val Malta         = CountryCode("MT")
  val Czech         = CountryCode("CZ")
  val Taiwan        = CountryCode("TW")
  val HongKong      = CountryCode("HK")
  val Macao         = CountryCode("MO")

  val CN = China
  val US = UnitedStates
  val CY = Cyprus
  val DK = Denmark
  val GB = UnitedKingdom
  val GR = Greece
  val NL = Netherlands
  val IE = Ireland
  val MY = Malaysia
  val NO = Norway
  val ZA = SouthAfrica
  val SG = Singapore
  val AU = Australia
  val DE = Germany
  val FR = France
  val FI = Finland
  val KR = SouthKorea
  val CA = Canada
  val JP = Japan
  val SE = Sweden
  val CH = Switzerland
  val NZ = NewZealand
  val IT = Italy
  val PL = Poland
  val UA = Ukraine
  val RU = Russia
  val EG = Egypt
  val PH = Philippines
  val AT = Austria
  val TH = Thailand
  val BG = Bulgaria
  val BE = Belgium
  val ES = Spain
  val HU = Hungary
  val CU = Cuba
  val PT = Portugal
  val RO = Romania
  val CM = Cameroon
  val DZ = Algeria
  val BY = Belarus
  val MU = Mauritius
  val LK = SriLanka
  val KG = Kyrgyzstan
  val LV = Latvia
  val LI = Liechtenstein
  val IL = Israel
  val LU = Luxembourg
  val MT = Malta
  val CZ = Czech
  val TW = Taiwan
  val HK = HongKong
  val MO = Macao

  val familiar = Seq(CN, US, GB, CA, DE, FR, IT, ES, PT, JP, KR, AU, NZ, PL, UA, RU, SG, TW, HK, MO, CY, DK, GR, NL, IE, MY, NO, ZA, FI, SE, CH, EG, PH, AT, TH, BG, BE, HU, CU, RO, CM, DZ, BY, MU, LK, KG, LV, LI, IL, LU, MT, CZ)

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