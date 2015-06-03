package controllers.sockets

import java.util.UUID

import actors.ChatWebSocket
import actors.ChatWebSocket._
import play.api.Play.current
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Controller, _}

import scala.concurrent.Future
import scala.util.Try

class Chat(
  implicit
  val messagesApi: MessagesApi
)
  extends Controller
  with I18nSupport {

  def connect: WebSocket[Try[Send], Received] =
    WebSocket.tryAcceptWithActor[Try[Send], Received] { implicit req =>
      Future.successful(
        req.session.get("usr_id").flatMap { id =>
          Try(UUID.fromString(id)).toOption
        } match {
          case None      => Left(Forbidden)
          case Some(uid) => Right(ChatWebSocket.props(_, uid))
        }
      )
    }
}