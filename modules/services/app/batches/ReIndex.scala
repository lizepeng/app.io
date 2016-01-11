package batches

import helpers.Logging
import org.joda.time.Interval
import play.api.libs.iteratee._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author zepeng.li@gmail.com
 */
class ReIndex[T](
  source: Enumerator[T],
  forEachChunk: List[T] => Future[_],
  onDone: => Unit = () => Unit
)(
  chunkSize: Int, val total: Option[Int] = None
)(
  implicit ec: ExecutionContext
)
  extends Batch[Int]
  with Logging {

  def start() = {
    Logger.info("Start indexing into elastic search.")

    source &>
      Enumeratee.grouped {
        Enumeratee.take(chunkSize) &>> Iteratee.getChunks
      } &>
      Enumeratee.onIterateeDone { () =>
        Logger.info("End indexing into elastic search.")
        onDone
      } |>>>
      Iteratee.foldM(0) { (c, list) =>
        forEachChunk(list).map { _ =>
          c + list.size
        }
      }
  }

  def progress: Int = ???

  def interval: Interval = ???

  def current: Int = ???

  def isCancelled: Boolean = ???

  def cancel(): Boolean = ???
}