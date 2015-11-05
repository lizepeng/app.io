package services.actors

import helpers._

/**
 * @author zepeng.li@gmail.com
 */
trait CanonicalNamedActor extends CanonicalNamed {

  def actorPath = s"/user/$basicName"
}

trait CanonicalNameAsShardName {
  self: CanonicalNamed =>

  def shardName: String = basicName
}