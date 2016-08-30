package messages

import akka.actor._
import helpers._
import models._
import services.actors._

import scala.language.postfixOps

object ChatActor
  extends ActorClusterSharding
    with ChatActorCNamed
    with CanonicalNameAsShardName {

  def props: Props = Props(classOf[ChatActor])
}

class ChatActor extends UserMessageActor with ChatActorCNamed {

  var _chatHistories: ChatHistories = _

  override def preStart() = {
    super.preStart()
    manager ! List(ChatHistory)
  }

  def isAllResourcesReady = super.isResourcesReady &&
    _chatHistories != null

  override def awaitingResources: Receive = ({

    case List(ch: ChatHistories) =>
      _chatHistories = ch
      tryToBecomeResourcesReady()

  }: Receive) orElse super.awaitingResources

  override def receiveCommand: Receive = ({

    case Envelope(_, msg: ChatMessage) =>
      _chatHistories.save(msg)
      sockets.route(msg, sender())

  }: Receive) orElse super.receiveCommand
}

trait ChatActorCNamed extends CanonicalNamed {

  def basicName = "chat_actors"
}