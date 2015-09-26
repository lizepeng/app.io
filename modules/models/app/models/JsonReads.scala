package models

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object JsonReads {

  def idReads(child: Symbol = 'id) =
    (__ \ child).read[UUID]

  def nameReads(child: Symbol = 'name) =
    (__ \ child).read[String](
      minLength[String](2)
        <~ maxLength[String](255)
    )

  def emailReads(child: Symbol = 'email) =
    (__ \ child).read[String](
      minLength[String](1)
        <~ maxLength[String](40)
        <~ email
    )
}