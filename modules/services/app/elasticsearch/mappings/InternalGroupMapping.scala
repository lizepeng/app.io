package elasticsearch.mappings

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings._
import elasticsearch.ElasticSearch._
import models.InternalGroups

/**
 * @author zepeng.li@gmail.com
 */
object InternalGroupMapping extends (InternalGroups => Iterable[TypedFieldDefinition]) {

  override def apply(v1: InternalGroups): Iterable[TypedFieldDefinition] = Seq(
    field(v1.id.name) typed StringType,
    field(v1.name.name) typed StringType index MappingParams.Index.NotAnalyzed,
    field(v1.layout.name) typed StringType index MappingParams.Index.NotAnalyzed
  )
}