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

  case object GetBasicPlayApi
  case object GetKeySpaceBuilder
}

class ResourcesMediator(
  implicit
  basicPlayApi: BasicPlayApi,
  contactPoint: KeySpaceBuilder,
  _chatHistories: ChatHistories,
  _mailInbox: MailInbox,
  _mailSent: MailSent
)
  extends Actor
  with ActorLogging {

  override def preStart() = {
    log.info("Started")
  }

  import ResourcesMediator._

  def receive = {

    case keys: List[Any] => sender ! keys.collect {
      case GetBasicPlayApi    => basicPlayApi
      case GetKeySpaceBuilder => contactPoint
      case ChatHistory        => _chatHistories
      case MailInbox          => _mailInbox
      case MailSent           => _mailSent
    }
  }
}