package services.actors

import akka.actor._

/**
 * A helper class which is used to append new methods to, for cluster actors.
 *
 * @author zepeng.li@gmail.com
 */
trait EntityAsDestination {

  /**
   * An actor in akka cluster can be located by region & id
   */
  trait Destination {

    /**
     * The actor id
     */
    def id: Any

    /**
     * The shard region
     */
    def region: ActorRef
  }
}