package controllers.helpers

import models.cfs.Block._
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{Enumeratee, Iteratee}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
object TrafficControl {

  val MAX_RATE = 3 * 1024

  object LimitTo {
    def apply(rate: Int = 1 MBps)(implicit ec: ExecutionContext): Enumeratee[BLK, BLK] = {
      Enumeratee.grouped {
        Enumeratee.take(16) &>>
          Iteratee.getChunks[BLK]
      } ><> Enumeratee.mapM[List[BLK]] {
        Promise.timeout(_, 36 * MAX_RATE / math.max(1, math.min(MAX_RATE, rate)), MILLISECONDS)(ec)
      } ><> Enumeratee.mapConcat[List[BLK]](_.toSeq)
    }
  }

  object UnLimited {
    def apply(): Enumeratee[BLK, BLK] = {
      Enumeratee.passAlong
    }
  }

  implicit class Int2Rate(val i: Int) extends AnyVal {

    def KBps: Int = i

    def MBps: Int = i * 1024
  }

  implicit class Double2Rate(val d: Double) extends AnyVal {

    def KBps: Int = d.toInt

    def MBps: Int = (d * 1024).toInt
  }

}