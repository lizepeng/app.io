package models

import helpers.HumanName
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
case class PersonalName(
  last: HumanName = HumanName.empty,
  first: HumanName = HumanName.empty,
  middle: Seq[HumanName] = Seq()
)

object PersonalName {

  def empty = PersonalName()

  implicit val jsonFormat = Json.format[PersonalName]
}