package services.actors

import helpers.CanonicalNamed

/**
 * @author zepeng.li@gmail.com
 */
trait CanonicalNamedActor extends CanonicalNamed {

  def actorPath = s"/user/$basicName"
}