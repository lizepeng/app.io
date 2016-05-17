package controllers.sockets

import controllers.actors.UserWebSocket._
import controllers.actors.WSProtocol._
import controllers.actors._
import helpers._
import models._
import play.api.i18n._
import play.api.mvc._
import security._

import scala.util.Try

class UserWebSocketCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
) extends Controller
  with BasicPlayComponents
  with I18nSupport {

  implicit lazy val errorHandler = new UserActionExceptionHandler with DefaultExceptionHandler

  def connect: WebSocket =
    MaybeUser().WebSocket[Try[Recv], Send](
      implicit req => user => UserWebSocket.props(_, user.id)
    )
}