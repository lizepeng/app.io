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
      (json \ "country").validate(CountryCode.jsonReads) match {
        case JsSuccess(cc, _) => cc match {
          case CountryCode.China        => AddressInChina.jsonFormat.reads(json)
          case CountryCode.Japan        => AddressInJapan.jsonFormat.reads(json)
          case CountryCode.UnitedStates => AddressInUnitedStates.jsonFormat.reads(json)
          case _                        => AddressInChina.jsonFormat.reads(json)
        }
        case e: JsError       => e
      }
    }
  }

  implicit val jsonWrites: Writes[Address] = new Writes[Address] {
    def writes(address: Address): JsValue = address match {
      case o: AddressInChina        => AddressInChina.jsonFormat.writes(o)
      case o: AddressInJapan        => AddressInJapan.jsonFormat.writes(o)
      case o: AddressInUnitedStates => AddressInUnitedStates.jsonFormat.writes(o)
    }
  }

  implicit val jsonFormat: Format[Address] = Format(jsonReads, jsonWrites)

  val default = AddressInChina()
}

case class AddressInChina(
  province: String = "",
  city: String = "",
  street1: String = "",
  street2: String = "",
  postal_code: String = "",
  country: CountryCode = CountryCode.China
) extends Address

object AddressInChina {

  implicit val jsonFormat = Json.format[AddressInChina]
}

case class AddressInJapan(
  postal_code: String = "",
  prefecture: String = "",
  country_city: String = "",
  further_divisions1: String = "",
  further_divisions2: String = "",
  country: CountryCode = CountryCode.Japan
) extends Address

object AddressInJapan {

  implicit val jsonFormat = Json.format[AddressInJapan]
}

case class AddressInUnitedStates(
  street1: String = "",
  street2: String = "",
  city: String = "",
  state: String = "",
  zip: String = "",
  country: CountryCode = CountryCode.UnitedStates
) extends Address

object AddressInUnitedStates {

  implicit val jsonFormat = Json.format[AddressInUnitedStates]
}