package batches

import akka.actor.Cancellable
import org.joda.time.Interval

/**
 * @author zepeng.li@gmail.com
 */
trait Batch[T] extends Cancellable {

  def progress: Int

  def total: Option[Int]

  def current: Int

  def interval: Interval
}
