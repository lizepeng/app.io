package messages

import akka.actor._
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import akka.persistence.PersistentActor
import akka.routing.{BroadcastRoutingLogic, Router}
import models.actors.ResourcesMediator

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
trait UserClusterSharding {

  def shardName: String

  def props: Props

  def startRegion(system: ActorSystem) = {
    ClusterSharding(system).start(
      typeName = shardName,
      entryProps = Some(props),
      idExtractor = idExtractor,
      shardResolver = shardResolver
    )
  }

  def getRegion(system: ActorSystem) = {
    ClusterSharding(system).shardRegion(shardName)
  }

  val idExtractor: ShardRegion.IdExtractor = {
    case env@Envelope(uid, _) => (uid.toString, env)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case Envelope(uid, _) => (math.abs(uid.hashCode) % 20000).toString
  }
}

object UserActor {

  case class Connect(socket: ActorRef)

  case object SetReceiveTimeout

}

abstract class UserActor extends PersistentActor with ActorLogging {

  import ShardRegion.Passivate

  val persistenceId  = s"${self.path.parent.name}-${self.path.name}"
  val receiveTimeout = 2 minute
  val mediator       = context.actorSelection(ResourcesMediator.actorPath)

  var sockets = Router(BroadcastRoutingLogic(), Vector())

  context become awaitingResources

  def awaitingResources: Receive

  def receiveCommand: Receive = {
    case Envelope(_, c: UserActor.Connect) =>
      connect(c.socket)

    case UserActor.SetReceiveTimeout =>
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
      self ! UserActor.SetReceiveTimeout

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
    self ! UserActor.SetReceiveTimeout
    unstashAll()
    context become receive
  }
}