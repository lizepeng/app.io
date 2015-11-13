package services.actors

import akka.actor._
import akka.contrib.pattern.ShardRegion
import akka.persistence.PersistentActor
import akka.util.Timeout
import helpers.BasicPlayApi

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

  import ResourcesMediator._
  import ShardRegion.Passivate

  val persistenceId = s"${self.path.parent.name}-${self.path.name}"
  val mediator      = context.actorSelection(ResourcesMediator.actorPath)

  var basicPlayApi  : BasicPlayApi = _
  var receiveTimeout: Timeout      = 2 minutes

  def isIdle: Boolean = true

  def isResourcesReady: Boolean = basicPlayApi != null && receiveTimeout != null

  def isAllResourcesReady: Boolean

  def receiveRecover: Receive = Actor.emptyBehavior

  override def preStart() = {
    super.preStart()
    context become awaitingResources
    mediator ! List(GetBasicPlayApi)
    self ! EntityActor.SetReceiveTimeout
  }

  // Not ready to receive message
  def awaitingResources: Receive = ({

    case List(bpa: BasicPlayApi) =>
      basicPlayApi = bpa
      receiveTimeout =
        bpa.configuration
          .getMilliseconds("app.akka.cluster.entity_actor.receive_timeout")
          .map(_ / 1000)
          .getOrElse(120L) seconds

      self ! EntityActor.SetReceiveTimeout
      tryToBecomeResourcesReady()

  }: Receive) orElse handleTimeout orElse stashAll

  final def tryToBecomeResourcesReady(): Unit = {
    if (isAllResourcesReady) {
      log.debug(s"${self.path.name}, All resources are ready.")
      resourcesReady()
    }
  }

  def resourcesReady(): Unit = {
    log.debug(s"${self.path.name}, Ready to receive messages.")
    becomeReceiveReady(receive)
  }

  def becomeReceiveReady(behavior: Actor.Receive): Unit = {
    unstashAll()
    context become behavior
  }

  // Ready to receive message
  def receiveCommand: Receive = handleTimeout

  def handleTimeout: Receive = {

    case EntityActor.SetReceiveTimeout =>
      if (isIdle) {
        log.debug(s"${self.path.name}, Set ReceiveTimeout: $receiveTimeout.")
        context.setReceiveTimeout(receiveTimeout.duration)
      }

    case EntityActor.UnsetReceiveTimeout =>
      if (!isIdle) {
        log.debug(s"${self.path.name}, Set no ReceiveTimeout.")
        context.setReceiveTimeout(Duration.Undefined)
      }

    case ReceiveTimeout =>
      if (isIdle) {
        context.parent ! Passivate(stopMessage = PoisonPill)
        log.debug(s"${self.path.name}, Passivate after being idle for $receiveTimeout.")
      }
  }

  def stashAll: Receive = {

    case msg => stash()

  }
}