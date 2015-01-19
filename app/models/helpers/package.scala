package models

import play.api.libs.iteratee.Enumeratee

/**
 * @author zepeng.li@gmail.com
 */
package object helpers {

  implicit def extendEnumeratee(e: Enumeratee.type): ExtendedEnumeratee.type = ExtendedEnumeratee

  object ExtendedEnumeratee {
    def flattenOption[A]: Enumeratee[Option[A], A] =
      Enumeratee.filter[Option[A]](_.isDefined) ><> Enumeratee.map(_.get)
  }

}