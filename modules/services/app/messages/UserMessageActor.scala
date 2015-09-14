package messages

import akka.actor._
import akka.contrib.pattern.ShardRegion
import akka.persistence.PersistentActor
import akka.routing.{BroadcastRoutingLogic, Router}
import models.actors.ResourcesMediator
import services.actors.Envelope

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */

object UserMessageActor {

  case class Connect(socket: ActorRef)

  case object SetReceiveTimeout

}

abstract class UserMessageActor extends PersistentActor with ActorLogging {

  import ShardRegion.Passivate

  val persistenceId  = s"${self.path.parent.name}-${self.path.name}"
  val receiveTimeout = 2 minute
  val mediator       = context.actorSelection(ResourcesMediator.actorPath)

  var sockets = Router(BroadcastRoutingLogic(), Vector())

  context become awaitingResources

  def awaitingResources: Receive

  def receiveCommand: Receive = {
    case Envelope(_, c: UserMessageActor.Connect) =>
      connect(c.socket)

    case UserMessageActor.SetReceiveTimeout =>
      if (sockets.routees.isEmpty) {
        log.debug(s"No connected sockets, passivate after $receiveTimeout.")
        context.setReceiveTimeout(receiveTimeout)
      }
  }

  def receiveRecover: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {

    case Terminated(a) =>
      context unwatch a
      sockets = sockets.removeRoutee(a)
      log.debug(s"Socket ${a.path} disconnected.")
      self ! UserMessageActor.SetReceiveTimeout

    case ReceiveTimeout =>
      if (sockets.routees.isEmpty) {
        context.parent ! Passivate(stopMessage = PoisonPill)
        log.debug("Passivate!")
      }

    case _ =>
      super.unhandled(msg)
  }

  def connect(a: ActorRef): Unit = {
    sockets = sockets.addRoutee(a)
    context watch a
    context.setReceiveTimeout(Duration.Undefined)
    log.debug(s"Socket ${a.path} connected.")
  }

  def becomeReceive(): Unit = {
    log.debug("Ready.")
    self ! UserMessageActor.SetReceiveTimeout
    unstashAll()
    context become receive
  }
}