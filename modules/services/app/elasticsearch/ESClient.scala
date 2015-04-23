package elasticsearch

import java.util.UUID

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.JsonDocumentSource
import helpers._
import play.api.libs.json.JsValue

/**
 * @author zepeng.li@gmail.com
 */
trait ESClient {
  self: ModuleLike =>

  val es_client = ElasticClient.local

  val indexName = "app_index"

  def esIndex(id: UUID, json: JsValue) = es_client.execute {
    index into indexName / moduleName id id doc JsonDocumentSource(json.toString())
  }

  def esSearch(keyword: String) = es_client.execute {
    search in indexName / moduleName query keyword
  }

  def esDelete(id: UUID) = es_client.execute {
    delete id id from s"$indexName/$moduleName"
  }

  def esUpdate(id: UUID, json: JsValue) = es_client.execute {
    update id id in s"$indexName/$moduleName" doc JsonDocumentSource(json.toString())
  }
}