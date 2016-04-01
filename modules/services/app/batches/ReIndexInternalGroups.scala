package batches

import elasticsearch.ElasticSearch
import elasticsearch.mappings.InternalGroupMapping
import helpers.BootingProcess
import models._

import scala.concurrent.ExecutionContext

/**
 * @author zepeng.li@gmail.com
 */
case class ReIndexInternalGroups(
  _internalGroups: InternalGroups
)(
  implicit
  es: ElasticSearch,
  ec: ExecutionContext
) extends ReIndex[Group](
  _internalGroups.all,
  list => es.BulkIndex(list) into _internalGroups
)(100) with BootingProcess {

  onStart(es.PutMapping(InternalGroupMapping)(_internalGroups))
}