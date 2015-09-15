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

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case Envelope(_, c: UserMessageActor.Connect) => connect(c.socket)
  }

  override def unhandled(msg: Any): Unit = msg match {

    case Terminated(a) =>
      log.debug(s"Socket ${a.path} disconnected.")
      context unwatch a
      sockets = sockets.removeRoutee(a)
      if (isIdle) self ! EntityActor.SetReceiveTimeout

    case _ =>
      super.unhandled(msg)
  }

  def connect(a: ActorRef): Unit = {
    log.debug(s"Socket ${a.path} connected.")
    sockets = sockets.addRoutee(a)
    context watch a
    self ! EntityActor.UnsetReceiveTimeout
  }
}