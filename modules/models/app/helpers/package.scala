import org.joda.time.DateTime
import play.api.i18n.Lang
import play.api.libs.Crypto
import play.api.libs.iteratee.Enumeratee
import play.api.mvc.QueryStringBindable
import play.api.mvc.QueryStringBindable.Parsing

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object helpers {

  implicit def extendEnumeratee(e: Enumeratee.type): ExtEnumeratee.type = ExtEnumeratee

  implicit def extendCrypto(c: Crypto.type): ExtCrypto.type = ExtCrypto

  //TODO override javascriptUnbind
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

  /**
   * QueryString binder for Seq
   */
  implicit def bindableSeq[T: QueryStringBindable]: QueryStringBindable[Seq[T]] =
    new QueryStringBindable[Seq[T]] {

      def bind(key: String, params: Map[String, Seq[String]]) = Some(
        Right(
          for {
            values <- params.get(key).flatMap(_.headOption).map(_.split(",")).toSeq
            rvalue <- values
            bound <- implicitly[QueryStringBindable[T]].bind(key, Map(key -> Seq(rvalue)))
            value <- bound.right.toOption
          } yield value
        )
      )

      def unbind(key: String, values: Seq[T]) =
        (for (value <- values) yield {
          implicitly[QueryStringBindable[T]].unbind(key, value).split('=')(1)
        }).mkString(",")

      override def javascriptUnbind = {
        val jsUnbindT = implicitly[QueryStringBindable[T]].javascriptUnbind
        s"""
          |function(k,vs){
          |  var l=vs&&vs.length,r=[],i=0;
          |  for(;i<l;i++){
          |   r[i]=($jsUnbindT)(k,vs[i]);
          |  }
          |  return r.join(',');
          |};
         """.stripMargin
      }
    }
}