package controllers.sockets

import java.util.UUID

import akka.actor._
import messages.ChatActor
import play.api.Play.current
import play.api.mvc.{Controller, _}

import scala.concurrent.Future
import scala.util.Try

object Chat extends Controller {

  def connect: WebSocket[String, String] =
    WebSocket.tryAcceptWithActor[String, String] { request =>
      Future.successful(
        request.session.get("usr_id").flatMap { id =>
          Try(UUID.fromString(id)).toOption
        } match {
          case None      => Left(Forbidden)
          case Some(uid) => Right(ChatWebSocketActor.props(_, uid))
        }
      )
    }
}

object ChatWebSocketActor {

  def props(
    out: ActorRef,
    user_id: UUID
  ) = Props(new ChatWebSocketActor(out, user_id))
}

class ChatWebSocketActor(out: ActorRef, uid: UUID) extends Actor {

  val chatActor = ChatActor.getRegion(context.system)
  chatActor ! ChatActor.Connect(uid, self)

  def receive = {
    case msg: String =>
      val split = msg.split(':')
      val to = Try(split(0)).getOrElse("")
      val text = Try(split(1)).getOrElse("")
      Try(UUID.fromString(to)).map { t =>
        out ! "Sent"
        chatActor ! ChatActor.Message(t, text, uid)
      }

    case ChatActor.Message(_, text, from) =>
      out ! s"$text, from $from"
  }
}