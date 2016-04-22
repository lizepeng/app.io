package models

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object summary {

  implicit def Int2Day(i: Int): Day = Day(i)
  implicit def Int2Month(i: Int): Month = Month(i)
  implicit def Int2Year(i: Int): Year = Year(i)
}