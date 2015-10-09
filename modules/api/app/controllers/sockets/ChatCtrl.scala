package controllers.sockets

import controllers.actors.ChatWebSocket
import controllers.actors.ChatWebSocket._
import helpers._
import models.Users
import play.api.i18n.I18nSupport
import play.api.mvc.{Controller, _}
import security._

import scala.util.Try

class ChatCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
)
  extends Controller
  with BasicPlayComponents
  with I18nSupport {

  def connect: WebSocket[Try[Send], Received] =
    MaybeUser().WebSocket[Try[Send], Received](
      user => ChatWebSocket.props(_, user.id),
      Forbidden
    )
}