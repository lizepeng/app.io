package elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import helpers._
import models._
import org.elasticsearch.action.admin.indices.mapping.delete.DeleteMappingResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait ES extends ModuleLike with AppConfig {

  import ESyntax._

  implicit lazy val Client = (
    for {
      settings <- config.getString("cluster.name").map { name =>
        ImmutableSettings
          .settingsBuilder()
          .put("cluster.name", name)
          .build()
      }
      uri <- config.getString("client.uri").map { uri =>
        ElasticsearchClientUri(uri)
      }
    } yield {
      ElasticClient.remote(settings, uri)
    })
    .getOrElse {
    Logger.warn("client.uri / cluster.name is not configured yet")
    ElasticClient.local
  }

  implicit val indexName = domain

  def Index[T <: HasID[_]](r: T) = new IndexAction(r)

  def Delete[ID](r: ID) = new DeleteAction(r)

  def Delete = new DeleteMappingAction

  def Update[T <: HasID[_]](r: T) = new UpdateAction(r)

  def BulkIndex[T <: HasID[_]](rs: Seq[T]) = new BulkIndexAction(rs)

  def Search(q: Option[String], p: Pager) = new SearchAction(q, p)

  def Multi(defs: (ES => ReadySearchDefinition)*) =
    new ReadyMultiSearchDefinition(Client)(
      defs.map(_(ES).definition)
    )

}

object ES extends ES

object ESyntax {

  class ReadyMultiSearchDefinition(client: ElasticClient)(
    val defs: Seq[SearchDefinition]
  ) {

    def future(): Future[MultiSearchResponse] = {
      client.execute(multi(defs))
    }
  }

  class SearchAction(q: Option[String], p: Pager)(
    implicit client: ElasticClient, indexName: String
  ) {

    def in(t: Module[_]): ReadySearchDefinition = {
      new ReadySearchDefinition(client, p)(
        Def(search in indexName / t.moduleName)
          .?(cond = true)(_ start p.start limit p.limit)
          .?(q.isDefined && q.get.nonEmpty)(_ query q.get)
          .result
      )
    }
  }

  class ReadySearchDefinition(client: ElasticClient, pager: Pager)(
    val definition: SearchDefinition
  ) {

    def future(): Future[PageSResp] = {
      client.execute(definition).map(PageSResp(pager, _))
    }
  }

  class IndexAction[T <: HasID[_]](r: T)(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: Module[T])(
      implicit converter: T => JsonDocSource
    ): Future[(JsValue, Future[IndexResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          index into indexName / t.moduleName id r.id doc src
        }
      )
    }
  }

  class DeleteAction[ID](id: ID)(
    implicit client: ElasticClient, indexName: String
  ) {

    def from[T <: HasID[ID]](t: Module[T]): Future[DeleteResponse] =
      client.execute {
        delete id id from s"$indexName/${t.moduleName}"
      }
  }

  class DeleteMappingAction(
    implicit client: ElasticClient, indexName: String
  ) {

    def from[T <: HasID[_]](t: Module[T]): Future[DeleteMappingResponse] =
      client.execute {
        delete mapping indexName / t.moduleName
      }
  }

  class UpdateAction[T <: HasID[_]](r: T)(
    implicit client: ElasticClient, indexName: String
  ) {

    def in(t: Module[T])(
      implicit converter: T => JsonDocSource
    ): Future[(JsValue, Future[UpdateResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          update id r.id in s"$indexName/${t.moduleName}" doc r
        }
      )
    }
  }

  class BulkIndexAction[T <: HasID[_]](rs: Seq[T])(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: Module[T])(
      implicit converter: T => JsonDocSource
    ): Future[BulkResponse] = client.execute {
      bulk(
        rs.map { r =>
          index into s"$indexName/${t.moduleName}" id r.id doc r
        }: _*
      )
    }
  }

  private case class Def[T](result: T) {

    def ?(cond: => Boolean)(append: T => T): Def[T] = {
      if (cond) Def(append(result)) else this
    }
  }

}