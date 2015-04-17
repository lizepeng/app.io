package helpers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumeratee

import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object ExtEnumeratee {

  def flattenOption[A]: Enumeratee[Option[A], A] =
    Enumeratee.filter[Option[A]](_.isDefined) ><> Enumeratee.map(_.get)
}