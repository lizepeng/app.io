package actors

import java.util.UUID

import akka.actor._
import messages.{ChatActor, Envelope}
import models.ChatMessage
import org.joda.time.DateTime
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter

import scala.language.{implicitConversions, postfixOps}
import scala.util.{Success, Try}

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

  chatActor ! Envelope(uid, ChatActor.Connect(self))

  def receive: Receive = {

    case ChatActor.Ready =>
      context become ready
  }

  def ready: Receive = {

    case Success(ChatWebSocket.Send(to, text)) =>
      if (text.nonEmpty)
        chatActor ! Envelope(to, ChatMessage(to, uid, text, DateTime.now))

    case ChatMessage(_, from, text, _) =>
      out ! ChatWebSocket.Received(from, text)
  }
}