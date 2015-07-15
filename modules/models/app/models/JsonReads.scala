package models

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object JsonReads {

  val idReads    = (__ \ 'id).read[UUID]
  val nameReads  = (__ \ 'name).read[String](minLength[String](2) <~ maxLength[String](255))
  val emailReads = (__ \ 'email).read[String](minLength[String](1) <~ maxLength[String](40) <~ email)
}