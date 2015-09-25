package services.actors

import akka.actor._
import akka.contrib.pattern.ShardRegion
import akka.persistence.PersistentActor
import akka.util.Timeout
import helpers.BasicPlayApi
import models.actors.ResourcesMediator

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */

object EntityActor {

  case object SetReceiveTimeout
  case object UnsetReceiveTimeout
}

abstract class EntityActor extends PersistentActor with ActorLogging {

  import ShardRegion.Passivate

  val persistenceId = s"${self.path.parent.name}-${self.path.name}"
  val mediator      = context.actorSelection(ResourcesMediator.actorPath)

  var basicPlayApi  : BasicPlayApi = _
  var receiveTimeout: Timeout      = _

  def isIdle: Boolean = true

  def isReady: Boolean

  def receiveRecover: Receive = Actor.emptyBehavior

  override def preStart() = {
    super.preStart()
    context become awaitingResources
    mediator ! ResourcesMediator.GetBasicPlayApi
  }

  def awaitingResources: Receive = {
    case bpa: BasicPlayApi =>
      basicPlayApi = bpa
      receiveTimeout =
        bpa.configuration
          .getMilliseconds("app.akka.cluster.entity_actor.receive_timeout")
          .map(_ millis)
          .map(_.toMinutes).getOrElse(2L) minutes

      tryToBecomeReceive()

    case msg => stash()
  }

  def receiveCommand: Receive = {

    case EntityActor.SetReceiveTimeout =>
      if (isIdle) {
        log.debug(s"${self.path} will passivate after $receiveTimeout.")
        context.setReceiveTimeout(receiveTimeout.duration)
      }

    case EntityActor.UnsetReceiveTimeout =>
      if (isIdle) {
        log.debug(s"${self.path} will activate.")
        context.setReceiveTimeout(Duration.Undefined)
      }
  }

  override def unhandled(msg: Any): Unit = msg match {

    case ReceiveTimeout =>
      if (isIdle) {
        context.parent ! Passivate(stopMessage = PoisonPill)
        log.debug(s"${self.path} passivate now.")
      }

    case _ =>
      super.unhandled(msg)
  }

  def tryToBecomeReceive(): Unit =
    if (isReady && basicPlayApi != null && receiveTimeout != null) {
      log.debug(s"${self.path} ready to receive messages")
      unstashAll()
      context become receive
      self ! EntityActor.SetReceiveTimeout
    }
}