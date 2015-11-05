package helpers

import org.joda.time._
import play.api.i18n.Lang
import play.api.mvc.QueryStringBindable
import play.api.mvc.QueryStringBindable.Parsing

/**
 * @author zepeng.li@gmail.com
 */
object ExtBindable {

  implicit object bindableQueryLang extends Parsing[Lang](
    Lang(_), _.code, (
    key: String,
    e: Exception
    ) => "Cannot parse parameter %s as Lang: %s".format(key, e.getMessage)
  )

  implicit object bindableQueryDateTime extends Parsing[DateTime](
    s => new DateTime(s.toLong), _.getMillis.toString, (
    key: String,
    e: Exception
    ) => "Cannot parse parameter %s as DateTime: %s".format(key, e.getMessage)
  )

  implicit object bindableQueryYearMonth extends Parsing[YearMonth](
    s => YearMonth.parse(s), _.toString, (
    key: String,
    e: Exception
    ) => "Cannot parse parameter %s as YearMonth: %s".format(key, e.getMessage)
  )

  /**
   * QueryString binder for Seq
   *
   * For example:
   * {{{
   *    bindableQuerySeq.bind("sort", Map("sort" -> Seq("name", "age")))
   *
   *    // Seq(name,age) <= sort=name,age
   *    // Seq()         <= ids=
   * }}}
   */
  implicit def bindableQuerySeq[T: QueryStringBindable]: QueryStringBindable[Seq[T]] =
    new QueryStringBindable[Seq[T]] {

      def bind(
        key: String, params: Map[String, Seq[String]]
      ): Option[Either[String, Seq[T]]] = {
        params.get(key).flatMap(_.headOption)
          .map(_.split(",").filter(_.nonEmpty)) // remove all empty substring
          .map { values =>
            val folder: Either[String, List[T]] = Right[String, List[T]](Nil)
            (folder /: values) { (eitherList, value) =>
            implicitly[QueryStringBindable[T]]
              .bind(key, Map(key -> Seq(value)))
              .map { eitherBound =>
              for {
                list <- eitherList.right
                bound <- eitherBound.right
              } yield bound +: list
            }.getOrElse(eitherList)
          }.right.map(_.toSeq.reverse)
          }
      }

      val pattern = """(.+)=(.+)""".r

      def unbind(key: String, values: Seq[T]) = {
        val collected: Seq[String] = values.map { v =>
          implicitly[QueryStringBindable[T]].unbind(key, v)
        }.collect {
          case pattern(_, value) => value
        }
        if (collected.isEmpty) ""
        else key + "=" + collected.mkString(",")
      }

      override def javascriptUnbind = {
        val jsUnbindT = implicitly[QueryStringBindable[T]].javascriptUnbind
        s"""
          |function(k,vs){
          | var l=vs&&vs.length,r=[],i=0;
          | for(;i<l;i++){
          |  r[i]=($jsUnbindT)(k,vs[i]);
          | }
          | return r.join(',');
          |};
         """.stripMargin
      }
    }
}