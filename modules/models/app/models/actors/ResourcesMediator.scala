package models.actors

import akka.actor._
import helpers.BasicPlayApi
import models._
import models.cassandra.KeySpaceBuilder

/**
 * @author zepeng.li@gmail.com
 */
object ResourcesMediator extends CanonicalNamedActor {

  override val basicName = "resources_mediator"

  def props(
    implicit
    basicPlayApi: BasicPlayApi,
    contactPoint: KeySpaceBuilder,
    _chatHistories: ChatHistories,
    _mailInbox: MailInbox,
    _mailSent: MailSent
  ): Props = Props(new ResourcesMediator)

  case object ModelRequired
  case object GetBasicPlayApi
}

class ResourcesMediator(
  implicit
  basicPlayApi: BasicPlayApi,
  contactPoint: KeySpaceBuilder,
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
    case ResourcesMediator.ModelRequired   => sender !(basicPlayApi, contactPoint)
    case ResourcesMediator.GetBasicPlayApi => sender ! basicPlayApi
    case ChatHistory.basicName             => sender ! _chatHistories
    case MailInbox.basicName               => sender ! _mailInbox
    case MailSent.basicName                => sender ! _mailSent
  }
}