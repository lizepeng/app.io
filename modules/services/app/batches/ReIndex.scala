package batches

import helpers.ModuleLike
import org.joda.time.Interval
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class ReIndex[T](
  source: Enumerator[T],
  forEachChunk: List[T] => Future[_],
  onDone: => Unit = () => Unit
)(chunkSize: Int, val total: Option[Int] = None)
  extends Batch[Int] with ModuleLike {

  override val moduleName = "reindex"

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