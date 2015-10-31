package elasticsearch

import com.sksamuel.elastic4s.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import play.api.mvc.QueryStringBindable

/**
 * @author zepeng.li@gmail.com
 */

trait SortField extends Any {

  def name: String

  def sortDef: FieldSortDefinition

  def toQueryString: String
}

object SortField {

  implicit def queryStringBinder: QueryStringBindable[SortField] =
    new QueryStringBindable[SortField] {

      val pattern1 = """([\w]+)""".r
      val pattern2 = """-([\w]+)""".r

      override def bind(
        key: String, params: Map[String, Seq[String]]
      ): Option[Either[String, SortField]] = {
        params.get(key).flatMap(_.headOption).map {
          case pattern1(word) => Right(new AscendingSortField(word))
          case pattern2(word) => Right(new DescendingSortField(word))
          case v              => Left(s"Cannot parse parameter $key with value '$v' as SortField.")
        }
      }

      override def unbind(
        key: String, field: SortField
      ): String = s"$key=${field.toQueryString}"
    }
}

class AscendingSortField(val name: String) extends AnyVal with SortField {

  def sortDef: FieldSortDefinition = new FieldSortDefinition(name).order(SortOrder.ASC)

  def toQueryString = name

  override def toString = toQueryString
}

class DescendingSortField(val name: String) extends AnyVal with SortField {

  def sortDef: FieldSortDefinition = new FieldSortDefinition(name).order(SortOrder.DESC)

  def toQueryString = s"-$name"

  override def toString = toQueryString
}