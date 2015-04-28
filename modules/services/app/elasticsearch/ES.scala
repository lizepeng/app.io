package elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import helpers._
import models._
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

  def Index[R <: HasID](r: R) = new IndexAction(r)

  def Delete[R <: HasID](r: R) = new DeleteAction(r)

  def Update[R <: HasID](r: R) = new UpdateAction(r)

  def BulkIndex[R](rs: Seq[R]) = new BulkIndexAction(rs)

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
          .?(q.isDefined)(_ query q.get)
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

  class IndexAction[R <: HasID](r: R)(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[(JsValue, Future[IndexResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          index into indexName / t.moduleName id r.id doc src
        }
      )
    }
  }

  class DeleteAction[R <: HasID](r: R)(
    implicit client: ElasticClient, indexName: String
  ) {

    def from(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[DeleteResponse] =
      client.execute {
        delete id r.id from s"$indexName/${t.moduleName}"
      }
  }

  class UpdateAction[R <: HasID](r: R)(
    implicit client: ElasticClient, indexName: String
  ) {

    def in(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[(JsValue, Future[UpdateResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          update id r.id in s"$indexName/${t.moduleName}" doc r
        }
      )
    }
  }

  class BulkIndexAction[R](rs: Seq[R])(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[BulkResponse] = client.execute {
      bulk(
        rs.map { r =>
          Def(index into s"$indexName/${t.moduleName}")
            .?(r.isInstanceOf[HasID])(_ id r.asInstanceOf[HasID].id)
            .?(cond = true)(_ doc r)
            .result
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