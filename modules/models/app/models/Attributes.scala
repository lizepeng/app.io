package models

import scala.reflect._

/**
 * @author zepeng.li@gmail.com
 */
case class Attributes(
  map: Map[String, Any] = Map.empty
) {

  def apply[T: ClassTag](key: String): Option[T] = {
    map.get(key).collect {
      case v if classTag[T].runtimeClass.isInstance(v) => v.asInstanceOf[T]
    }
  }

  def +(entry: (String, Any)): Attributes = copy(map + entry)
}