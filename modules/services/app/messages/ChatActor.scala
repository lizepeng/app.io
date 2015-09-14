package messages

import akka.actor._
import models._
import services.actors._

import scala.language.postfixOps

object ChatActor extends ActorClusterSharding {

  val shardName: String = "chat_actors"

  def props: Props = Props(classOf[ChatActor])
}

class ChatActor extends UserMessageActor {

  var _chatHistories: ChatHistories = _

  override def preStart() = {
    mediator ! ChatHistory.basicName
    super.preStart()
  }

  def awaitingResources: Receive = {
    case ch: ChatHistories =>
      _chatHistories = ch
      becomeReceive()

    case msg => stash()
  }

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case Envelope(_, msg: ChatMessage) =>
      _chatHistories.save(msg)
      sockets.route(msg, sender())
  }
}