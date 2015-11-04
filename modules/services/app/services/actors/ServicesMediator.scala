package services.actors

import akka.actor._
import elasticsearch.ElasticSearch
import models.actors.CanonicalNamedActor
import services._

/**
 * @author zepeng.li@gmail.com
 */
object ServicesMediator extends CanonicalNamedActor {

  override val basicName = "services_mediator"

  def props(
    implicit
    elasticSearch: ElasticSearch,
    mailService: MailService
  ): Props = Props(new ServicesMediator)

  case object ModelRequired
  case object GetBasicPlayApi
}

class ServicesMediator(
  implicit
  elasticSearch: ElasticSearch,
  mailService: MailService
)
  extends Actor
  with ActorLogging {

  override def preStart() = {
    log.info("Started")
  }

  def receive = {
    case ElasticSearch.basicName => sender ! elasticSearch
    case MailService.basicName   => sender ! mailService
  }
}