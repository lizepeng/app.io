package messages

import akka.actor._
import models._

import scala.language.postfixOps

object ChatActor extends UserClusterSharding {

  val shardName: String = "chat_actors"

  def props: Props = Props(classOf[ChatActor])
}

class ChatActor extends UserActor {

  var _chatHistories: ChatHistories = _

  override def preStart() = {
    modelsGuide ! ChatHistory.basicName
    super.preStart()
  }

  def receiveCommand: Receive = {

    case ch: ChatHistories =>
      _chatHistories = ch
      becomeReady()
      context become readyCommand
  }

  def readyCommand: Receive = {

    case Envelope(_, msg: ChatMessage) =>
      _chatHistories.save(msg)
      sockets.route(msg, sender())
  }

  def releaseResources(): Unit = {
    _chatHistories = null
  }
}