package batches

import elasticsearch.ElasticSearch
import models.{Group, InternalGroups}

import scala.concurrent.ExecutionContext

/**
 * @author zepeng.li@gmail.com
 */
case class ReIndexInternalGroups(
  es: ElasticSearch,
  _internalGroups: InternalGroups
)(
  implicit executor: ExecutionContext
) extends ReIndex[Group](
  _internalGroups.all,
  list => es.BulkIndex(list) into _internalGroups
)(100)