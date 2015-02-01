package helpers

import org.joda.time._

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object syntax {

  def iff[A](con: Boolean)(f: => A): Boolean = { if (con) f; con }

  implicit class SugarAndThen[A](val a: A) extends AnyVal {

    def andThen(blk: A => Unit) = { blk(a); a }

  }

  implicit class SugarOption[A](val opt: Option[A]) extends AnyVal {

    def whenEmpty(blk: => Unit) = { if (opt.isEmpty) blk; opt }

    def whenDefined(blk: A => Unit) = { if (opt.isDefined) blk(opt.get); opt }

    def flatMapM[B](f: A => Future[Option[B]]): Future[Option[B]] = {
      if (opt.isEmpty) Future.successful(None)
      else f(opt.get)
    }
  }

  implicit class SugarBoolean(val b: Boolean) extends AnyVal {

    def option[A](func: => A): Option[A] = if (b) Some(func) else None

    def flatOption[A](func: => Option[A]): Option[A] = if (b) func else None

    def otherwise[A](func: => Option[A]): Option[A] = if (!b) func else None
  }

  implicit class SugarArray[T](val array: Array[T]) extends AnyVal {

    def getOrElse(idx: Int, t: T) = if (array.isDefinedAt(idx)) array(idx) else t

  }

  /*
   * Ruby Style Grammar
   */
  implicit class IntToTimes(n: Int) {

    def times[A](blk: => A) = for (i <- 1 to n) yield blk

  }

  implicit class BlockToUnless[T](left: => T) {

    def iff(right: => Boolean): Option[T] = if (right) Some(left) else None

    def unless(right: => Boolean): Option[T] = if (!right) Some(left) else None

  }

  /**
   *
   * @param i
   */
  implicit class Int2DateTime(val i: Int) extends AnyVal {
    def day: Period = days

    def days: Period = Period.days(i)

    def month: Period = months

    def months: Period = Period.months(i)

    def week: Period = weeks

    def weeks: Period = Period.weeks(i)

    def year: Period = years

    def years: Period = Period.years(i)
  }

  trait PolarQuestion {
    def ? : Boolean
  }

}