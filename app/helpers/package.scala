import play.api.libs.iteratee.Enumeratee

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
package object helpers {
  implicit def extendEnumeratee(e: Enumeratee.type): ExtEnumeratee.type = ExtEnumeratee
}