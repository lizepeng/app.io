package actors

import java.util.UUID

import akka.actor._
import messages.ChatActor
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter

import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
object ChatWebSocket {

  def props(out: ActorRef, user_id: UUID) =
    Props(new ChatWebSocket(out, user_id))

  case class Send(to: UUID, text: String)

  case class Received(from: UUID, text: String)

  implicit val in_fmt        = Json.format[Send]
  implicit val in_frame_fmt  = classFrame[Send]
  implicit val out_fmt       = Json.format[Received]
  implicit val out_frame_fmt = FrameFormatter.jsonFrame[Received]
}

class ChatWebSocket(out: ActorRef, uid: UUID) extends Actor {

  val chatActor = ChatActor.getRegion(context.system)

  override def preStart() = {
    chatActor ! ChatActor.Connect(uid, self)
  }

  def receive = {
    case Success(ChatWebSocket.Send(to, text)) =>
      chatActor ! ChatActor.Message(to, text, uid)

    case ChatActor.Message(_, text, from) =>
      out ! ChatWebSocket.Received(from, text)

  }
}