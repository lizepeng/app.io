package controllers.actors

import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
trait WSProtocol {

  import WSProtocol._

  def name: String

  def jsFmtRecv: Format[Recv]
  def jsFmtSend: Format[Send]

  def protocolError = JsError(s"error.ws.protocol.$name")
}

object WSProtocol {
  trait Recv {

    def code: Int
    def protocol: String
  }
  trait Send {

    def code: Int
    def protocol: String
  }

  implicit val jsFmtRecv = new Format[Recv] {
    def reads(json: JsValue): JsResult[Recv] = {
      (json \ "protocol").validate[String] match {
        case JsSuccess(ChatProtocol.name, _)         =>
          Json.fromJson(json)(ChatProtocol.jsFmtRecv)
        case JsSuccess(NotificationProtocol.name, _) =>
          Json.fromJson(json)(NotificationProtocol.jsFmtRecv)
        case _                                       =>
          unknownProtocolError
      }
    }
    def writes(o: Recv): JsValue = o.protocol match {
      case ChatProtocol.name         =>
        Json.toJson(o)(ChatProtocol.jsFmtRecv)
      case NotificationProtocol.name =>
        Json.toJson(o)(NotificationProtocol.jsFmtRecv)
    }
  }

  implicit val jsFmtSend = new Format[Send] {
    def reads(json: JsValue): JsResult[Send] = {
      (json \ "protocol").validate[String] match {
        case JsSuccess(ChatProtocol.name, _)         =>
          Json.fromJson(json)(ChatProtocol.jsFmtSend)
        case JsSuccess(NotificationProtocol.name, _) =>
          Json.fromJson(json)(NotificationProtocol.jsFmtSend)
        case _                                       =>
          unknownProtocolError
      }
    }
    def writes(o: Send): JsValue = o.protocol match {
      case ChatProtocol.name         =>
        Json.toJson(o)(ChatProtocol.jsFmtSend)
      case NotificationProtocol.name =>
        Json.toJson(o)(NotificationProtocol.jsFmtSend)
    }
  }

  def unknownProtocolError = JsError(s"error.ws.protocol.unknown")
}