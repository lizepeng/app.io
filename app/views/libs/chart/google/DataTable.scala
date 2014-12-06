package views.libs.chart.google

import org.joda.time._

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object DataTable {
  val Columns = "cols"
  val Rows    = "rows"

  object Col {
    val Label    = "label"
    val DataType = "type"
    val Pattern  = "pattern"
    val Id       = "id"
    val Property = "p"
  }

  object Row {
    val Cells = "c"
  }

  object Cell {
    val Value  = "v"
    val Format = "f"
  }

  object Type {
    val Bool      = "boolean"
    val Number    = "number"
    val String    = "string"
    val Date      = "date"
    val DateTime  = "datetime"
    val TimeOfDay = "timeofday"
  }

  implicit class JsonDateTime(val dt: DateTime) extends AnyVal {
    def toJson = s"Date(${dt.getYear},${dt.getMonthOfYear - 1},${dt.getDayOfMonth})"
  }

  implicit class JsonLocalDate(val dt: LocalDate) extends AnyVal {
    def toJson = s"Date(${dt.getYear},${dt.getMonthOfYear - 1},${dt.getDayOfMonth})"
  }

  implicit class JsonYearMonth(val dt: YearMonth) extends AnyVal {
    def toJson = s"Date(${dt.getYear},${dt.getMonthOfYear - 1})"
  }

}