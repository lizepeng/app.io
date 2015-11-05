package messages

import java.util.UUID

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
    mediator ! List(MailInbox, MailSent)
  }

  def isAllResourcesReady = super.isResourcesReady &&
    _mailInbox != null &&
    _mailSent != null

  override def awaitingResources: Receive = ({
    case List(mi: MailInbox, ms: MailSent) =>
      _mailInbox = mi
      _mailSent = ms
      tryToBecomeResourcesReady()

  }: Receive) orElse super.awaitingResources

  override def receiveCommand: Receive = ({

    case Envelope(uid: UUID, mail: Mail) =>
      _mailInbox.save(uid, mail)
      _mailSent.save(mail)
      sockets.route(mail, sender())

  }: Receive) orElse super.receiveCommand
}