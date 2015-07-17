package models.actors

import akka.actor._
import models._

/**
 * @author zepeng.li@gmail.com
 */
object ModelsGuide extends CanonicalNamedActor {

  override val basicName = "models_guide"

  def props(
    implicit
    _chatHistories: ChatHistories,
    _mailInbox: MailInbox,
    _mailSent: MailSent
  ): Props = Props(new ModelsGuide)
}

class ModelsGuide(
  implicit
  val _chatHistories: ChatHistories,
  val _mailInbox: MailInbox,
  val _mailSent: MailSent
)
  extends Actor
  with ActorLogging {

  override def preStart() = {
    log.info("started")
  }

  def receive = {
    case ChatHistory.basicName => sender ! _chatHistories
    case MailInbox.basicName   => sender ! _mailInbox
    case MailSent.basicName    => sender ! _mailSent
  }
}