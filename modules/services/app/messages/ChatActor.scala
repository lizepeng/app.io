package messages

import akka.actor._
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import akka.persistence.PersistentActor
import akka.routing._
import models._
import models.actors.ModelsGuide

import scala.concurrent.duration._
import scala.language.postfixOps

object ChatActor {

  val shardName: String = "chat_actors"

  def props: Props = Props(classOf[ChatActor])

  case class Connect(socket: ActorRef)

  case object Ready

  val idExtractor: ShardRegion.IdExtractor = {
    case env@Envelope(uid, _) => (uid.toString, env)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case Envelope(uid, _) => (math.abs(uid.hashCode) % 20000).toString
  }

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
}

class ChatActor
  extends PersistentActor
  with ActorLogging {

  import ShardRegion.Passivate

  override def persistenceId: String =
    s"${self.path.parent.name}-${self.path.name}"

  var sockets = Router(BroadcastRoutingLogic(), Vector())

  var _chatHistories: Option[ChatHistories] = None

  val receiveTimeout = 2 minute

  val modelsGuide = context.actorSelection(ModelsGuide.actorPath)

  override def preStart() = {
    modelsGuide ! ChatHistory.basicName
    super.preStart()
  }

  def receiveCommand: Receive = {

    case Envelope(_, c: ChatActor.Connect) =>
      connect(c.socket)

    case ch: ChatHistories =>
      _chatHistories = Some(ch)
      context become readyCommand
      log.info("ready.")
      sockets.route(ChatActor.Ready, self)
      trySetReceiveTimeout()
  }

  def readyCommand: Receive = {

    case Envelope(_, c: ChatActor.Connect) =>
      connect(c.socket)
      c.socket ! ChatActor.Ready

    case Envelope(_, msg: ChatMessage) =>
      _chatHistories.foreach { ch =>
        ch.save(msg)
        sockets.route(msg, sender())
      }

    case Terminated(a) =>
      context unwatch a
      sockets = sockets.removeRoutee(a)
      log.debug(s"${a.path} disconnected.")
      trySetReceiveTimeout()
  }

  private def connect(socket: ActorRef): Unit = {
    context watch socket
    sockets = sockets.addRoutee(socket)
    context.setReceiveTimeout(Duration.Undefined)
    log.debug(s"${socket.path} connected.")
  }

  private def trySetReceiveTimeout(): Unit = {
    if (sockets.routees.isEmpty) {
      log.debug(s"no connected sockets, passivate after $receiveTimeout.")
      context.setReceiveTimeout(receiveTimeout)
    }
  }

  def receiveRecover: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout =>
      if (sockets.routees.isEmpty) {
        _chatHistories = None
        context.parent ! Passivate(stopMessage = PoisonPill)
        log.debug("passivate")
      }
    case _              =>
      super.unhandled(msg)
  }
}