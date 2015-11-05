package elasticsearch

import helpers.Logging
import models.cassandra.Indexable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
case class ESIndexCleaner(
  tables: Indexable[_]*
)(implicit es: ElasticSearch, ec: ExecutionContext) extends Logging {

  def dropIndexIfEmpty: Future[Boolean] = {
    Future.sequence(
      tables.map { table =>
        (for {
          _empty <- table.isEmpty
          result <-
          if (_empty) {
            (es.Delete from table).map(_ => true)
          }
          else
            Future.successful(false)
        } yield result).andThen {
          case Success(true) =>
            Logger.debug(s"Cleaned elastic search index ${table.basicName}.")
        }.recover {
          case e: Throwable => Logger.debug(e.getMessage); false
        }
      }
    ).map(rets => (true /: rets)(_ && _))
  }
}