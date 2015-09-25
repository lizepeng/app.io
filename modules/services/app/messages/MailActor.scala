package messages

import akka.actor._
import models._
import services.actors._

import scala.language.postfixOps

object MailActor extends ActorClusterSharding {

  val shardName: String = "mail_actors"

  def props: Props = Props(classOf[MailActor])
}

class MailActor extends UserMessageActor {

  var _mailInbox: MailInbox = _
  var _mailSent : MailSent  = _

  override def preStart() = {
    super.preStart()
    mediator ! MailInbox.basicName
    mediator ! MailSent.basicName
  }

  def isReady = _mailInbox != null && _mailSent != null

  override def awaitingResources: Receive = ({
    case mi: MailInbox =>
      _mailInbox = mi
      tryToBecomeReceive()

    case ms: MailSent =>
      _mailSent = ms
      tryToBecomeReceive()

  }: Receive) orElse super.awaitingResources

  override def receiveCommand: Receive = super.receiveCommand orElse {
    case Envelope(uid, mail: Mail) =>
      _mailInbox.save(uid, mail)
      _mailSent.save(mail)
      sockets.route(mail, sender())
  }
}