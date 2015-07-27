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

  case object Ready

}

abstract class UserActor
  extends PersistentActor
  with ActorLogging {

  import ShardRegion.Passivate

  override def persistenceId: String =
    s"${self.path.parent.name}-${self.path.name}"

  protected val receiveTimeout = 2 minute

  protected val mediator = context.actorSelection(ResourcesMediator.actorPath)

  protected var sockets = Router(BroadcastRoutingLogic(), Vector())

  private var isReady: Boolean = false

  def receiveRecover: Receive = Actor.emptyBehavior

  def releaseResources(): Unit

  override def unhandled(msg: Any): Unit = msg match {

    case Envelope(_, c: UserActor.Connect) =>
      connect(c.socket)
      if (isReady) c.socket ! UserActor.Ready

    case Terminated(a) =>
      context unwatch a
      sockets = sockets.removeRoutee(a)
      log.debug(s"${a.path} disconnected.")
      trySetReceiveTimeout()

    case ReceiveTimeout =>
      if (sockets.routees.isEmpty) {
        isReady = false
        releaseResources()
        context.parent ! Passivate(stopMessage = PoisonPill)
        log.debug("passivate!")
      }

    case _ =>
      super.unhandled(msg)
  }

  def trySetReceiveTimeout(): Unit = {
    if (sockets.routees.isEmpty) {
      log.debug(s"no connected sockets, passivate after $receiveTimeout.")
      context.setReceiveTimeout(receiveTimeout)
    }
  }

  def connect(a: ActorRef): Unit = {
    sockets = sockets.addRoutee(a)
    context watch a
    context.setReceiveTimeout(Duration.Undefined)
    log.debug(s"${a.path} connected.")
  }

  def becomeReady(): Unit = {
    isReady = true
    log.info("ready.")
    sockets.route(UserActor.Ready, self)
    trySetReceiveTimeout()
  }
}