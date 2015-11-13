package messages

import akka.actor._
import akka.routing.{BroadcastRoutingLogic, Router}
import services.actors.{EntityActor, Envelope}

import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */

object UserMessageActor {

  case class Connect(socket: ActorRef)
}

abstract class UserMessageActor extends EntityActor {

  var sockets = Router(BroadcastRoutingLogic(), Vector())

  override def isIdle = sockets.routees.isEmpty

  override def receiveCommand: Receive = ({

    case Envelope(_, c: UserMessageActor.Connect) =>
      log.debug(s"Socket ${c.socket.path} connected")
      sockets = sockets.addRoutee(c.socket)
      context watch c.socket
      self ! EntityActor.UnsetReceiveTimeout
      log.debug(s"${self.path.name}, Now has ${sockets.routees.length} sockets connected.")

    case Terminated(a) =>
      log.debug(s"Socket ${a.path} Disconnected.")
      context unwatch a
      sockets = sockets.removeRoutee(a)
      if (isIdle) self ! EntityActor.SetReceiveTimeout
      log.debug(s"${self.path.name}, Now has ${sockets.routees.length} sockets connected.")

  }: Receive) orElse super.receiveCommand
}