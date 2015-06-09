package elasticsearch

import helpers.Logging
import models.cassandra.EntityTable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
case class ESIndexCleaner(
  tables: EntityTable[_]*
)(implicit es: ElasticSearch, executor: ExecutionContext) extends Logging {

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
            Logger.info(s"Cleaned elastic search index ${table.basicName}.")
        }
      }
    ).map(rets => (true /: rets)(_ && _))
  }
}