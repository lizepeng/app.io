package controllers.actors

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import play.api.i18n.Messages
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object NotificationProtocol extends WSProtocol {

  import WSProtocol._

  val name = "notification"

  case class Notify(
    content: String,
    code: Int = 0,
    protocol: String = name
  ) extends Send
  object Notify {implicit val jsFmt = Json.format[Notify]}

  val jsFmtRecv = new Format[Recv] {
    def reads(json: JsValue): JsResult[Recv] = protocolError
    def writes(o: Recv): JsValue = JsNull
  }

  val jsFmtSend = new Format[Send] {
    def reads(json: JsValue): JsResult[Send] = {
      (json \ "code").validate[Int] match {
        case JsSuccess(0, _) => Json.fromJson(json)(Notify.jsFmt)
        case _               => protocolError
      }
    }
    def writes(o: Send): JsValue = o match {
      case s: Notify => Json.toJson(s)(Notify.jsFmt)
    }
  }

  def receive(out: ActorRef)(
    implicit messages: Messages
  ): PartialFunction[Any, Unit] = {

    case notify: String =>
      out ! Notify(notify)

  }: Receive
}