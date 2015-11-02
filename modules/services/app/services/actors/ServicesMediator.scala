package services.actors

import akka.actor._
import elasticsearch.ElasticSearch
import models.actors.CanonicalNamedActor

/**
 * @author zepeng.li@gmail.com
 */
object ServicesMediator extends CanonicalNamedActor {

  override val basicName = "services_mediator"

  def props(
    implicit
    es: ElasticSearch
  ): Props = Props(new ServicesMediator)

  case object ModelRequired
  case object GetBasicPlayApi
}

class ServicesMediator(
  implicit
  es: ElasticSearch
)
  extends Actor
  with ActorLogging {

  override def preStart() = {
    log.info("Started")
  }

  def receive = {
    case ElasticSearch.basicName => sender ! es
  }
}