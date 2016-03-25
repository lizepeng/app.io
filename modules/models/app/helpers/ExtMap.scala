package helpers

/**
 * @author zepeng.li@gmail.com
 */
object ExtMap {

  /**
   * We need the following method because
   * [[scala.collection.immutable.MapLike.mapValues]] will generate a collection
   * which can not be serialized, and sometimes one may send such an instance to a remote
   * actor by accident.
   */
  implicit class RichMap[A, B](val map: Map[A, B]) extends AnyVal {

    def mapValuesSafely[C](f: B => C): Map[A, C] = map.mapValues(f).map(identity)
  }
}