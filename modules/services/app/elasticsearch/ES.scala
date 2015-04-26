package elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import helpers._
import models._
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object ES extends ModuleLike with AppConfig {

  lazy val Client = (
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

  val indexName = domain

  def Index[R <: HasID](r: R) = new IndexAction(r)

  def Delete[R <: HasID](r: R) = new DeleteAction(r)

  def Update[R <: HasID](r: R) = new UpdateAction(r)

  def BulkIndex[R](rs: Seq[R]) = new BulkIndexAction(rs)

  def Search(q: Option[String], p: Pager) = new SearchAction(q, p)

  class SearchAction(q: Option[String], p: Pager) {

    def in(t: Module[_]): Future[ESPage] = {
      Client.execute {
        Def(search in indexName / t.moduleName)
          .?(cond = true)(_ start p.start limit p.limit)
          .?(q.isDefined)(_ query q.get)
          .result
      }.map(ESPage(p, _))
    }
  }

  class IndexAction[R <: HasID](r: R) {

    def into(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[(JsValue, Future[IndexResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, Client.execute {
          index into indexName / t.moduleName id r.id doc src
        }
      )
    }
  }

  class DeleteAction[R <: HasID](r: R) {

    def from(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[DeleteResponse] =
      Client.execute {
        delete id r.id from s"$indexName/${t.moduleName}"
      }
  }

  class UpdateAction[R <: HasID](r: R) {

    def in(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[(JsValue, Future[UpdateResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, Client.execute {
          update id r.id in s"$indexName/${t.moduleName}" doc r
        }
      )
    }
  }

  class BulkIndexAction[R](rs: Seq[R]) {

    def into(t: Module[R])(
      implicit converter: R => JsonDocSource
    ): Future[BulkResponse] = Client.execute {
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