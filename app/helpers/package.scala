import org.joda.time.DateTime
import play.api.i18n.Lang
import play.api.libs.Crypto
import play.api.libs.iteratee.Enumeratee
import play.api.mvc.QueryStringBindable.Parsing

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object helpers {

  implicit def extendEnumeratee(e: Enumeratee.type): ExtEnumeratee.type = ExtEnumeratee

  implicit def extendCrypto(c: Crypto.type): ExtCrypto.type = ExtCrypto

  implicit object bindableQueryLang extends Parsing[Lang](
    Lang(_), _.code, (key: String, e: Exception) => "Cannot parse parameter %s as Lang: %s".format(key, e.getMessage)
  )

  implicit object bindableQueryDateTime extends Parsing[DateTime](
    s => new DateTime(s.toLong), _.getMillis.toString, (key: String, e: Exception) => "Cannot parse parameter %s as DateTime: %s".format(key, e.getMessage)
  )

}