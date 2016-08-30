package services.actors

import akka.actor._
import akka.persistence._
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
 * The protocol ensures that message should be sent and received only once.
 *
 * @author zepeng.li@gmail.com
 */
object ExactlyOnceDelivery extends EventDef {

  /**
   * Message Definitions.
   */
  object Messages {

    /** The message which need to be confirmed. */
    case class ToConfirm[PL](serial: Long, payload: PL)

    /** The confirmation of a message. */
    case class Confirm(serial: Long)
  }

  /**
   * Event Definitions.
   */
  object Events {

    /** The Command has already been sent. */
    case class CommandSent[ID, CMDs <: CommandDef](to: ID, cmd: CMDs#Command) extends Event

    /** The Notification has already been sent. */
    case class NotificationSent[ID](to: ID, nti: NotificationDef#Notification) extends Event

    /** The Message has already been confirmed. */
    case class Confirmed(serial: Long) extends Event

    /** The Message has already been received. */
    case class Received(serial: Long, sender: String) extends Event

    object NotificationSent {

      implicit def jsonFormat[ID](
        implicit fmt1: Format[ID], fmt2: Format[NotificationDef#Notification]
      ): Format[NotificationSent[ID]] = (
        /**/ (__ \ "to").format[ID] and
        /**/ (__ \ "nti").format[NotificationDef#Notification]) (
        NotificationSent.apply, unlift(NotificationSent.unapply)
      )
    }

    object Confirmed {

      implicit val jsonFormat = Format.of[Long].inmap[Confirmed](Confirmed.apply, _.serial)
    }
  }

  /**
   * The helper for building an cluster actor.
   */
  trait Helper {
    self: EntityAsDestination with AtLeastOnceDelivery with ActorLogging =>

    /**
     * Sent a message which need to be confirmed.
     */
    def deliver[ID, PL](region: ActorRef, to: ID, payload: PL): Unit = {
      deliver(region.path) { serial =>
        log.debug(s"#1 (re)sent/recover to $to, serial=$serial")
        Envelope(to, Messages.ToConfirm(serial, payload))
      }
    }

    /**
     * Sent a confirmation of a message back.
     */
    implicit class _ConfirmReceivableDestination(des: Destination) {

      def !!(serial: Long): Unit = {
        log.debug(s"#2 confirmation sent to ${des.id}, serial=$serial")
        des.region ! Envelope(des.id, Messages.Confirm(serial))
      }
    }

    /**
     * Handle the confirmation sent back from the receiver.
     */
    def handleEODConfirm: Receive = {
      case Envelope(id, Messages.Confirm(serial)) =>
        log.debug(s"#3 confirmed at $id, serial=$serial")
        persist(Events.Confirmed(serial))(handleEODConfirmed)
    }

    /**
     * Confirm a message.
     */
    def handleEODConfirmed: Receive = {
      case Events.Confirmed(serial) => confirmDelivery(serial)
    }
  }
}