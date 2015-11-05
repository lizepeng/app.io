package elasticsearch.mappings

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings._
import elasticsearch.ElasticSearch
import models.Users

/**
 * Elastic search mapping definition for [[Users]]
 *
 * Since field email should be treated as a one string,
 * in other words, it should not be split into several words by special character, such as '@',
 *
 * @author zepeng.li@gmail.com
 */
object UserMapping extends (Users => Iterable[TypedFieldDefinition]) {
  override def apply(v1: Users): Iterable[TypedFieldDefinition] = Seq(
    v1.id.name typed StringType,
    v1.name.name typed StringType,
    v1.email.name typed StringType analyzer ElasticSearch.analyzers.email
  )
}