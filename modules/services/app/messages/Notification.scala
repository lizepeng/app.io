package messages

import akka.actor._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator._

object Notification {

  def props(name: String): Props = Props(classOf[Notification], name)

  case class Publish(msg: String)

  case class Connect(socket: ActorRef)

  case class Message(from: String, text: String)

}

class Notification(name: String) extends Actor {

  val mediator = DistributedPubSubExtension(context.system).mediator
  val topic    = "chatroom"
  mediator ! Subscribe(topic, self)
  mediator ! Put(self)

  var socket: ActorRef = null

  def receive = {
    case Notification.Connect(sc) =>
      socket = sc

    case Notification.Publish(msg) =>
      mediator ! Publish(topic, Notification.Message(name, msg))

    case msg@Notification.Message(from, text) =>
      val direction = if (sender == self) ">>>>" else s"<< $from:"
      println(s"$name $direction $text")
      socket ! msg
  }
}