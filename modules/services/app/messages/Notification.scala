package messages

import akka.actor._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator._

import scala.concurrent.duration._
import scala.language.postfixOps

object Notification {

  def props: Props = Props(classOf[Notification])
}

class Notification extends Actor {

  val mediator = DistributedPubSubExtension(context.system).mediator

  val topic = "chatroom"

  context.system.scheduler.schedule(0 second, 10 seconds) {
    mediator ! Publish(topic, "welcome to the chatroom!")
  }(context.system.dispatcher)

  def receive = Actor.emptyBehavior
}