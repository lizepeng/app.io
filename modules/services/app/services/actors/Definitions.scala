package services.actors

import helpers.ExtReads._
import helpers._

/**
 * Abstract definition -- Event
 *
 * @author zepeng.li@gmail.com
 */
trait EventDef {
  trait Event extends Serializable
}

/**
 * Abstract definition -- Command
 *
 * @author zepeng.li@gmail.com
 */
trait CommandDef {

  /** Common trait for all commands. */
  trait Command extends Serializable
}

/**
 * Abstract definition -- Notification
 *
 * @author zepeng.li@gmail.com
 */
trait NotificationDef {

  /** Message passed among cluster actors, which is not a command. */
  trait Notification extends Serializable with CanonicalNamed
}

object NotificationDef {

  private type NTF = NotificationDef#Notification

  trait Format[S <: NTF] extends NamedJsObjectFormat[NTF, S]

  implicit val jsonFormat = NamedJsObjectFormat.jsonFormat[NTF]()
}