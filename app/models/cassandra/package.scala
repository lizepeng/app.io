package models

import com.websudos.phantom.Implicits._

/**
 * @author zepeng.li@gmail.com
 */
package object cassandra {

  implicit class RichRow(val r: Row) extends AnyVal {
    def applied: Boolean = r.getBool("[applied]")
  }

}