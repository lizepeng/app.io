package controllers.actors

import java.util.UUID

import akka.actor._
import messages._
import models._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter
import services.actors.Envelope

import scala.language.{implicitConversions, postfixOps}
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
object ChatWebSocket {

  def props(out: ActorRef, user_id: UUID) =
    Props(new ChatWebSocket(out, user_id))

  case class Send(to: UUID, text: String)

  case class Received(from: UUID, text: String)

  implicit def in_frame_fmt(
    implicit message: Messages
  ): FrameFormatter[Try[Send]] = jsonFrame[Send]

  implicit val in_fmt        = Json.format[Send]
  implicit val out_fmt       = Json.format[Received]
  implicit val out_frame_fmt = FrameFormatter.jsonFrame[Received]
}

class ChatWebSocket(out: ActorRef, uid: UUID) extends Actor {

  val chatActor = ChatActor.getRegion(context.system)

  chatActor ! Envelope(uid, UserMessageActor.Connect(self))

  def receive: Receive = {

    case Success(ChatWebSocket.Send(to, text)) =>
      if (text.nonEmpty)
        chatActor ! Envelope(to, ChatMessage(to, uid, text))

    case ChatMessage(_, from, text, _) =>
      out ! ChatWebSocket.Received(from, text)
  }
}