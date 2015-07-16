package models

import java.util.UUID

/**
 * @author zepeng.li@gmail.com
 */
trait MessageLike {

  def from: UUID

  def text: String
}