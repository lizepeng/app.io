package messages

import akka.actor._
import models._

import scala.language.postfixOps

object MailActor extends UserClusterSharding {

  val shardName: String = "mail_actors"

  def props: Props = Props(classOf[MailActor])
}

class MailActor extends UserActor {

  var _mailInbox: MailInbox = _
  var _mailSent : MailSent  = _

  override def preStart() = {
    mediator ! MailInbox.basicName
    mediator ! MailSent.basicName
    super.preStart()
  }

  def awaitingResources: Receive = {
    case mi: MailInbox =>
      _mailInbox = mi
      tryToBecomeReady()

    case ms: MailSent =>
      _mailSent = ms
      tryToBecomeReady()

    case msg => stash()
  }

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case Envelope(uid, mail: Mail) =>
      _mailInbox.save(uid, mail)
      _mailSent.save(mail)
      sockets.route(mail, sender())
  }

  def tryToBecomeReady(): Unit = {
    if (_mailInbox != null && _mailSent != null) becomeReceive()
  }
}