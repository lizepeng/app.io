package models

import scala.reflect._

/**
 * @author zepeng.li@gmail.com
 */
case class Preferences(
  map: Map[String, Any] = Map.empty
) {

  def get[T: ClassTag](key: String): Option[T] = {
    map.get(key).collect {
      case v if classTag[T].runtimeClass.isInstance(v) => v.asInstanceOf[T]
    }
  }

  def +(entry: (String, Any)): Preferences = copy(map + entry)
}