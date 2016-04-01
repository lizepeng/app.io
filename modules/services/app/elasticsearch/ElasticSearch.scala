package elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.analyzers._
import com.sksamuel.elastic4s.mappings._
import helpers._
import models._
import models.cassandra.ESIndexable
import models.misc._
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentBuilder
import play.api.libs.json.JsValue

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class ElasticSearch(
  val basicPlayApi: BasicPlayApi
) extends AppConfigComponents
  with PackageNameAsCanonicalName
  with BasicPlayComponents
  with DefaultPlayExecutor
  with BootingProcess
  with Logging {

  applicationLifecycle.addStopHook { () =>
    Logger.info(s"Shutdown Elastic Search Client")
    Future.successful(Client.close())
  }

  import ESyntax._

  implicit lazy val Client = (
    for {
      settings <- config.getString("cluster.name").map { name =>
        Settings
          .settingsBuilder()
          .put("cluster.name", name)
          .build()
      }
      uri <- config.getString("client.uri").map { uri =>
        ElasticsearchClientUri(uri)
      }
    } yield {
      ElasticClient.transport(settings, uri)
    })
    .getOrElse {
      Logger.warn("client.uri / cluster.name is not configured yet")
      ElasticClient.local
    }

  implicit val indexName = domain

  // create index with customized analyzers
  onStart(
    Client.execute {
      create index indexName analysis(
        ElasticSearch.Analyzers.defaultDef,
        ElasticSearch.Analyzers.emailDef)
    }.recover {
      // if index already exists
      case e: Throwable => Logger.debug(e.getMessage)
    }
  )

  def PutMapping[T <: ESIndexable[_]](
    mapping: T => Iterable[TypedFieldDefinition]
  )(implicit t: T) = Client.execute {
    put mapping indexName / t.basicName fields mapping(t)
  }

  def Index[T <: HasID[_]](r: T) = new IndexAction[T](r)

  def Delete[ID](r: ID) = new DeleteAction[ID](r)

  def Update[T <: HasID[_]](r: T) = new UpdateAction[T](r)

  def BulkIndex[T <: HasID[_]](rs: Seq[T]) = new BulkIndexAction[T](rs)

  def Search(
    q: Option[String],
    p: Pager,
    sort: Seq[SortField],
    lowercase_expanded_terms: Option[Boolean] = None
  ) = new SearchAction(q, p, sort, lowercase_expanded_terms)

  def Multi(defs: (ElasticSearch => ReadySearchDefinition)*) =
    new ReadyMultiSearchDefinition(Client)(
      defs.map(_ (this).definition)
    )

}

object ElasticSearch extends CanonicalNamed {

  override val basicName: String = "elastic_search"

  object MappingParams {

    object Index {

      val No          = "no"
      val NotAnalyzed = "not_analyzed"
      val Analyzed    = "analyzed"
    }
  }

  object Analyzers {

    val emailDef = CustomAnalyzerDefinition("email", UaxUrlEmailTokenizer)
    val email    = CustomAnalyzer("email")

    val defaultDef = new AnalyzerDefinition("default") {
      def build(source: XContentBuilder): Unit = {
        source.field("type", "smartcn")
      }
    }
  }

}

object ESyntax {

  class ReadyMultiSearchDefinition(client: ElasticClient)(
    val defs: Seq[SearchDefinition]
  ) {

    def future(): Future[MultiSearchResult] = {
      client.execute(multi(defs))
    }
  }

  class SearchAction(
    q: Option[String],
    p: Pager,
    sort: Seq[SortField],
    lowercase_expanded_terms: Option[Boolean]
  )(
    implicit client: ElasticClient, indexName: String
  ) {

    def in(t: ESIndexable[_]): ReadySearchDefinition = {
      val sortable = t.sortable.map(_.name)

      val sortDefs = sort.collect {
        case f if sortable.contains(f.name) => f.sortDef
      }

      new ReadySearchDefinition(client, p)(
        Def(search in indexName / t.basicName)
          .?(cond = true)(_ start p.start limit p.limit)
          .?(q.isDefined && q.get.nonEmpty)(
            _ query Def(new QueryStringQueryDefinition(q.get))
              .?(lowercase_expanded_terms.isDefined)(
                _ lowercaseExpandedTerms lowercase_expanded_terms.get
              ).result
          )
          .?(sortDefs.nonEmpty)(_ sort (sortDefs: _*))
          .result
      )
    }
  }

  class ReadySearchDefinition(client: ElasticClient, pager: Pager)(
    val definition: SearchDefinition
  ) {

    def future()(implicit ec: ExecutionContext): Future[PageSResp] = {
      client.execute(definition).map(PageSResp(pager, _))
    }
  }

  class IndexAction[T <: HasID[_]](r: T)(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: ESIndexable[T])(
      implicit converter: T => JsonDocSource
    ): Future[(JsValue, Future[IndexResult])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          index into indexName / t.basicName id r.id doc src
        }
      )
    }
  }

  class DeleteAction[ID](id: ID)(
    implicit client: ElasticClient, indexName: String
  ) {

    def from[T <: HasID[ID]](t: ESIndexable[T]): Future[DeleteResponse] =
      client.execute {
        delete id id from s"$indexName/${t.basicName}"
      }
  }

  class UpdateAction[T <: HasID[_]](r: T)(
    implicit client: ElasticClient, indexName: String
  ) {

    def in(t: ESIndexable[T])(
      implicit converter: T => JsonDocSource
    ): Future[(JsValue, Future[UpdateResponse])] = {
      val src = converter(r)
      Future.successful(
        (src.jsval, client.execute {
          update id r.id in s"$indexName/${t.basicName}" doc src
        })
      )
    }
  }

  class BulkIndexAction[T <: HasID[_]](rs: Seq[T])(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: ESIndexable[T])(
      implicit converter: T => JsonDocSource
    ): Future[BulkResult] = client.execute {
      bulk(
        rs.map { r =>
          index into s"$indexName/${t.basicName}" id r.id doc r
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