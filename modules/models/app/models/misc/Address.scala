package models.misc

import helpers._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
sealed trait Address {

  /**
   * Defined which country this address is in.
   *
   * @return A CountryCode which contains an ISO 3166 two-letter country code.
   */
  def country: CountryCode
}

object Address extends AddressJsonStringifier

trait AddressJsonStringifier extends JsonStringifier[Address] {

  implicit val jsonReads: Reads[Address] = new Reads[Address] {
    def reads(json: JsValue): JsResult[Address] = {
      import CountryCode._
      (json \ "country").validate(CountryCode.jsonReads) match {
        case JsSuccess(cc, _) => cc match {
          case CN | ZA | KR | CA | IT | UA | RU | ES | BY => Address001.jsonFormat.reads(json)
          case TH                                         => Address002.jsonFormat.reads(json)
          case PH                                         => Address003.jsonFormat.reads(json)
          case EG                                         => Address004.jsonFormat.reads(json)
          case NZ                                         => Address005.jsonFormat.reads(json)
          case AU                                         => Address006.jsonFormat.reads(json)
          case MY                                         => Address007.jsonFormat.reads(json)
          case GB | IE                                    => Address008.jsonFormat.reads(json)
          case CM                                         => Address009.jsonFormat.reads(json)
          case US                                         => Address010.jsonFormat.reads(json)
          case JP                                         => Address011.jsonFormat.reads(json)
          case TW                                         => Address012.jsonFormat.reads(json)
          case HK                                         => Address013.jsonFormat.reads(json)
          case MO                                         => Address014.jsonFormat.reads(json)
          case _                                          => Address100.jsonFormat.reads(json)
        }
        case e: JsError       => e
      }
    }
  }

  implicit val jsonWrites: Writes[Address] = new Writes[Address] {
    def writes(address: Address): JsValue = address match {
      case o: Address001 => Address001.jsonFormat.writes(o)
      case o: Address002 => Address002.jsonFormat.writes(o)
      case o: Address003 => Address003.jsonFormat.writes(o)
      case o: Address004 => Address004.jsonFormat.writes(o)
      case o: Address005 => Address005.jsonFormat.writes(o)
      case o: Address006 => Address006.jsonFormat.writes(o)
      case o: Address007 => Address007.jsonFormat.writes(o)
      case o: Address008 => Address008.jsonFormat.writes(o)
      case o: Address009 => Address009.jsonFormat.writes(o)
      case o: Address010 => Address010.jsonFormat.writes(o)
      case o: Address011 => Address011.jsonFormat.writes(o)
      case o: Address012 => Address012.jsonFormat.writes(o)
      case o: Address013 => Address013.jsonFormat.writes(o)
      case o: Address014 => Address014.jsonFormat.writes(o)
      case o: Address100 => Address100.jsonFormat.writes(o)
    }
  }

  implicit val jsonFormat: Format[Address] = Format(jsonReads, jsonWrites)

  val default = Address001(country = CountryCode.CN)
}

/**
 * CN, ZA, KR, CA, IT, UA, RU, ES, BY
 */
case class Address001(
  province: String = "",
  city: String = "",
  street1: String = "",
  street2: String = "",
  postal_code: String = "",
  country: CountryCode
) extends Address

object Address001 {val jsonFormat = Json.format[Address001]}

/**
 * TH
 */
case class Address002(
  street2: String = "",
  street1: String = "",
  district: String = "", //subdivision
  province: String = "",
  postal_code: String = "",
  country: CountryCode
) extends Address

object Address002 {val jsonFormat = Json.format[Address002]}

/**
 * PH
 */
case class Address003(
  street2: String = "",
  street1: String = "",
  district: String = "", //subdivision
  city: String = "",
  post_code: String = "",
  country: CountryCode
) extends Address

object Address003 {val jsonFormat = Json.format[Address003]}

/**
 * EG
 */
case class Address004(
  street2: String = "",
  street1: String = "",
  district: String = "",
  governorate: String = "",
  country: CountryCode
) extends Address

object Address004 {val jsonFormat = Json.format[Address004]}

/**
 * NZ
 */
case class Address005(
  street2: String = "",
  street1: String = "",
  suburb: String = "",
  city: String = "",
  postal_code: String = "",
  country: CountryCode
) extends Address

object Address005 {val jsonFormat = Json.format[Address005]}

/**
 * AU
 */
case class Address006(
  street2: String = "",
  street1: String = "",
  suburb: String = "",
  state: String = "",
  postal_code: String = "",
  country: CountryCode
) extends Address

object Address006 {val jsonFormat = Json.format[Address006]}

/**
 * MY
 */
case class Address007(
  street2: String = "",
  street1: String = "",
  city: String = "",
  state: String = "",
  postal_code: String = "",
  country: CountryCode
) extends Address

object Address007 {val jsonFormat = Json.format[Address007]}

/**
 * GB, IE
 */
case class Address008(
  street2: String = "",
  street1: String = "",
  city: String = "",
  county: String = "",
  post_code: String = "",
  country: CountryCode
) extends Address

object Address008 {val jsonFormat = Json.format[Address008]}

/**
 * CM
 */
case class Address009(
  street2: String = "",
  street1: String = "",
  city: String = "",
  country: CountryCode
) extends Address

object Address009 {val jsonFormat = Json.format[Address009]}

/**
 * US
 */
case class Address010(
  street1: String = "",
  street2: String = "",
  city: String = "",
  state: String = "",
  zip: String = "",
  country: CountryCode
) extends Address

object Address010 {val jsonFormat = Json.format[Address010]}

/**
 * JP
 */
case class Address011(
  postal_code: String = "",
  prefecture: String = "",
  country_city: String = "",
  further_divisions1: String = "",
  further_divisions2: String = "",
  country: CountryCode
) extends Address

object Address011 {val jsonFormat = Json.format[Address011]}

/**
 * TW
 */
case class Address012(
  street1: String = "",
  street2: String = "",
  township: String = "", //city
  county: String = "", //district
  zip: String = "",
  country: CountryCode
) extends Address

object Address012 {val jsonFormat = Json.format[Address012]}

/**
 * HK
 */
case class Address013(
  street1: String = "",
  street2: String = "",
  district: String = "",
  region: String = "",
  country: CountryCode
) extends Address

object Address013 {val jsonFormat = Json.format[Address013]}

/**
 * MO
 */
case class Address014(
  street1: String = "",
  street2: String = "",
  city: String = "",
  region: String = "",
  country: CountryCode
) extends Address

object Address014 {val jsonFormat = Json.format[Address014]}

/**
 * CY, DK, GR, NL, NO, SG, DE, FR, FI, SE, CH, PL, AT, BG, BE, HU, CU, PT, RO, DZ, MU, LK, KG, LV, LI, IL, LU, MT, CZ
 */
case class Address100(
  street2: String = "",
  street1: String = "",
  city: String = "",
  postal_code: String = "",
  country: CountryCode
) extends Address

object Address100 {val jsonFormat = Json.format[Address100]}