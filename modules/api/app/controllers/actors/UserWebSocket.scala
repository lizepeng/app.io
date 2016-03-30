package controllers.actors

import java.util.UUID

import akka.actor._
import messages._
import play.api.i18n.Messages
import play.api.mvc.WebSocket.MessageFlowTransformer
import services.actors.Envelope

import scala.language.{implicitConversions, postfixOps}
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
object UserWebSocket {

  import WSProtocol._

  def props(out: ActorRef, user_id: UUID)(
    implicit messages: Messages
  ) = Props(new UserWebSocket(out, user_id))

  implicit def userWebSocketMessageFlowTransformer(
    implicit message: Messages
  ): MessageFlowTransformer[Try[Recv], Send] = {
    caseClassMessageFlowTransformer[Recv, Send]
  }
}

class UserWebSocket(out: ActorRef, uid: UUID)(
  implicit messages: Messages
) extends Actor {

  val notificationActor = NotificationActor.getRegion(context.system)
  val chatActor         = ChatActor.getRegion(context.system)

  chatActor ! Envelope(uid, UserMessageActor.Connect(self))
  notificationActor ! Envelope(uid, UserMessageActor.Connect(self))

  def receive: Receive = Actor.emptyBehavior orElse
    ChatProtocol.receive(out, uid, chatActor) orElse
    NotificationProtocol.receive(out)
}