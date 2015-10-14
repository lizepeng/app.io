package controllers.sockets

import controllers.actors.UserWebSocket._
import controllers.actors.WSProtocol._
import controllers.actors._
import helpers._
import models.Users
import play.api.i18n.I18nSupport
import play.api.mvc._
import security._

import scala.util.Try

class UserWebSocketCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
)
  extends Controller
  with BasicPlayComponents
  with I18nSupport {

  def connect: WebSocket[Try[Recv], Send] =
    MaybeUser().WebSocket[Try[Recv], Send](
      implicit req => user => UserWebSocket.props(_, user.id),
      Forbidden
    )
}