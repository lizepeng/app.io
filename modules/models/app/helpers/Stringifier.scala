package helpers

import java.util.UUID

/**
 * @author zepeng.li@gmail.com
 */
trait Stringifier[T] {

  def << : String => T

  def >>: : T => String
}

object Stringifier {

  implicit val uuidSerializer = new Stringifier[UUID] {
    def << = UUID.fromString

    def >>: = _.toString
  }

  implicit val stringSerializer = new Stringifier[String] {
    def << = s => s

    def >>: = s => s
  }
}