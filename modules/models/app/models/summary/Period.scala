package models.summary

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
case class Daily[K, V](
  data: Map[K, V] = Map()
) extends AnyVal

object Daily {

  implicit def DailyToMap[K, V](daily: Daily[K, V]): Map[K, V] = daily.data
}

case class Monthly[K, V](
  data: Map[K, V] = Map()
) extends AnyVal

object Monthly {

  implicit def MonthlyToMap[K, V](monthly: Monthly[K, V]): Map[K, V] = monthly.data
}

case class Yearly[K, V](
  data: Map[K, V] = Map()
) extends AnyVal

object Yearly {

  implicit def YearlyToMap[K, V](yearly: Yearly[K, V]): Map[K, V] = yearly.data
}

case class Day(self: Int) extends AnyVal


object Day {

  implicit def Day2Int(d: Day): Int = d.self
}

case class Month(self: Int) extends AnyVal


object Month {

  implicit def Month2Int(m: Month): Int = m.self
}

case class Year(self: Int) extends AnyVal

object Year {

  implicit def Year2Int(y: Year): Int = y.self
}