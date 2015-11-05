package messages

import akka.actor._
import services.actors._

import scala.language.postfixOps

object NotificationActor
  extends ActorClusterSharding {

  val shardName: String = "notification_actors"

  def props: Props = Props(classOf[NotificationActor])

}

class NotificationActor extends UserMessageActor {

  def isAllResourcesReady = {
    super.isResourcesReady
  }

  override def receiveCommand: Receive = ({

    case Envelope(_, notify: String) =>
      sockets.route(notify, sender())

  }: Receive) orElse super.receiveCommand

}

trait NotificationRegionComponents {
  self: AkkaTimeOutConfig =>

  def actorSystem: ActorSystem

  def _notificationRegion = NotificationActor.getRegion(actorSystem)
}