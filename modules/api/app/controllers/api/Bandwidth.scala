package controllers.api

import helpers.Contexts.trafficShaperContext
import helpers._
import models.cfs.Block._
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumeratee.CheckDone
import play.api.libs.iteratee._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object Bandwidth {

  private object Config extends ModuleLike with AppConfig {

    override val moduleName = "bandwidth"

    lazy val max = config.getBytes("max").map(_.toInt).getOrElse(5 MBps)
    lazy val min = config.getBytes("min").map(_.toInt).getOrElse(200 KBps)
  }

  import Config._

  object LimitTo {

    def apply(rate: Int = 1 MBps): Enumeratee[BLK, BLK] = limitTo(
      if (rate < min) min
      else if (rate > max) max
      else rate
    )

  }

  object UnLimited {

    def apply(): Enumeratee[BLK, BLK] = Enumeratee.passAlong
  }

  private def limitTo(rate: Int)(
    implicit ec: ExecutionContext
  ): Enumeratee[BLK, BLK] = new CheckDone[BLK, BLK] {

    def step[B](remaining: Int, start: Long)(
      k: K[BLK, B]
    ): K[BLK, Iteratee[BLK, B]] = {

      case in@Input.El(_) if remaining <= 1 =>
        new CheckDone[BLK, BLK] {
          def continue[A](k: K[BLK, A]) = {
            val spent = now - start
            val cont = Cont(step(rate / 4, now)(k))
            Iteratee.flatten {
              if (spent > 500) Future.successful(cont)
              else Promise.timeout(cont, 500 - spent, MILLISECONDS)(ec)
            }
          }
        } &> k(in)

      case in@Input.El(block) if remaining > 1 =>
        new CheckDone[BLK, BLK] {
          def continue[A](k: K[BLK, A]) =
            Cont(step(remaining - block.size, start)(k))
        } &> k(in)

      case Input.Empty if remaining > 0 =>
        new CheckDone[BLK, BLK] {
          def continue[A](k: K[BLK, A]) =
            Cont(step(remaining, start)(k))
        } &> k(Input.Empty)

      case Input.EOF => Done(Cont(k), Input.EOF)

      case in => Done(Cont(k), in)
    }

    def continue[A](k: K[BLK, A]): Iteratee[BLK, Iteratee[BLK, A]] =
      Cont(step(rate / 4, now)(k))
  }

  private def now = DateTime.now.getMillis

  implicit class Int2Rate(val i: Int) extends AnyVal {

    def KBps: Int = i * 1024

    def MBps: Int = i * 1024 * 1024
  }

  implicit class Double2Rate(val d: Double) extends AnyVal {

    def KBps: Int = (d * 1024).toInt

    def MBps: Int = (d * 1024 * 1024).toInt
  }

}