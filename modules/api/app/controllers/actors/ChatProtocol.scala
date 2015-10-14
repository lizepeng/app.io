package controllers.actors

import java.util.UUID

import akka.actor.Actor._
import akka.actor.ActorRef
import models.ChatMessage
import play.api.libs.json._
import services.actors.Envelope

import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
object ChatProtocol extends WSProtocol {

  import WSProtocol._

  val name = "chat"

  case class SendTo(
    to: UUID,
    text: String,
    code: Int = 0,
    protocol: String = name
  ) extends Recv
  object SendTo {implicit val jsFmt = Json.format[SendTo]}

  case class ReceivedFrom(
    from: UUID,
    text: String,
    code: Int = 0,
    protocol: String = name
  ) extends Send
  object ReceivedFrom {implicit val jsFmt = Json.format[ReceivedFrom]}

  val jsFmtRecv = new Format[Recv] {
    def reads(json: JsValue): JsResult[Recv] = {
      (json \ "code").validate[Int] match {
        case JsSuccess(0, _) => Json.fromJson(json)(SendTo.jsFmt)
        case _               => protocolError
      }
    }
    def writes(o: Recv): JsValue = o match {
      case s: SendTo => Json.toJson(s)(SendTo.jsFmt)
    }
  }

  val jsFmtSend = new Format[Send] {
    def reads(json: JsValue): JsResult[Send] = {
      (json \ "code").validate[Int] match {
        case JsSuccess(0, _) => Json.fromJson(json)(ReceivedFrom.jsFmt)
        case _               => protocolError
      }
    }
    def writes(o: Send): JsValue = o match {
      case s: ReceivedFrom => Json.toJson(s)(ReceivedFrom.jsFmt)
    }
  }

  def receive(out: ActorRef, uid: UUID, chatActor: ActorRef): PartialFunction[Any, Unit] = {

    case Success(SendTo(to, text, _, _)) =>
      if (text.nonEmpty)
        chatActor ! Envelope(to, ChatMessage(to, uid, text))

    case ChatMessage(_, from, text, _) =>
      out ! ReceivedFrom(from, text)

  }: Receive
}