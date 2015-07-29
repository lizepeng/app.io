package models.actors

import akka.actor._
import com.websudos.phantom.dsl._
import helpers.BasicPlayApi
import models._

/**
 * @author zepeng.li@gmail.com
 */
object ResourcesMediator extends CanonicalNamedActor {

  override val basicName = "resources_mediator"

  def props(
    implicit
    basicPlayApi: BasicPlayApi,
    cassandraManager: CassandraManager,
    _chatHistories: ChatHistories,
    _mailInbox: MailInbox,
    _mailSent: MailSent
  ): Props = Props(new ResourcesMediator)

  case object ModelRequired

}

class ResourcesMediator(
  implicit
  basicPlayApi: BasicPlayApi,
  cassandraManager: CassandraManager,
  val _chatHistories: ChatHistories,
  val _mailInbox: MailInbox,
  val _mailSent: MailSent
)
  extends Actor
  with ActorLogging {

  override def preStart() = {
    log.info("Started")
  }

  def receive = {
    case ResourcesMediator.ModelRequired => sender !(basicPlayApi, cassandraManager)
    case ChatHistory.basicName           => sender ! _chatHistories
    case MailInbox.basicName             => sender ! _mailInbox
    case MailSent.basicName              => sender ! _mailSent
  }
}