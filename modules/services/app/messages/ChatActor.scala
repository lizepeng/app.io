package messages

import java.util.UUID

import akka.actor._
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import akka.persistence.PersistentActor
import akka.routing._

import scala.concurrent.duration._

object ChatActor {

  val shardName: String = "ChatActor"

  def props: Props = Props(classOf[ChatActor])

  sealed trait Command {def uid: UUID}

  case class Connect(uid: UUID, socket: ActorRef) extends Command

  case class Message(uid: UUID, text: String, from: UUID) extends Command

  val idExtractor: ShardRegion.IdExtractor = {
    case cmd: Command =>
      (cmd.uid.toString, cmd)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case cmd: Command =>
      (math.abs(cmd.uid.hashCode) % 20000).toString
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

class ChatActor extends PersistentActor {

  import ShardRegion.Passivate

  override def persistenceId: String =
    self.path.parent.name + "-" + self.path.name

  var sockets = Router(BroadcastRoutingLogic(), Vector())

  override def receiveCommand: Receive = {
    case ChatActor.Connect(_, a) =>
      context watch a
      sockets = sockets.addRoutee(a)
      if (sockets.routees.isEmpty)
        context.setReceiveTimeout(Duration.Undefined)

    case msg@ChatActor.Message(_, text, from) =>
      sockets.route(msg, sender())

    case Terminated(a) =>
      context unwatch a
      sockets = sockets.removeRoutee(a)
      if (sockets.routees.isEmpty)
        context.setReceiveTimeout(2.minutes)
  }

  override def receiveRecover: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout =>
      if (sockets.routees.isEmpty)
        context.parent ! Passivate(stopMessage = PoisonPill)
    case _              =>
      super.unhandled(msg)
  }
}