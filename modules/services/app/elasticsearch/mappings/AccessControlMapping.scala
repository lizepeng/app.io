package elasticsearch.mappings

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings._
import elasticsearch.ElasticSearch._
import models.AccessControls

/**
 * @author zepeng.li@gmail.com
 */
object AccessControlMapping extends (AccessControls => Iterable[TypedFieldDefinition]) {

  override def apply(v1: AccessControls): Iterable[TypedFieldDefinition] = Seq(
    field(v1.principal_id.name) typed StringType index MappingParams.Index.NotAnalyzed,
    field(v1.resource.name) typed StringType index MappingParams.Index.NotAnalyzed
  )
}