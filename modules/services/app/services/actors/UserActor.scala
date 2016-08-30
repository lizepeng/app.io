package services.actors

import akka.actor._
import akka.pattern._
import akka.persistence._
import helpers._
import models._
import models.misc._
import play.api.libs.json._
import services.actors.{ExactlyOnceDelivery => EOD}

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * UserActor's Companion Object
 *
 * @author zepeng.li@gmail.com
 */
object UserActor extends ActorClusterSharding
  with UserCommands
  with UserCanonicalNamed
  with CanonicalNameAsShardName {

  def props: Props = Props(classOf[UserActor])
}

/**
 * Commands/Notifications in [[UserActor]]
 */
trait UserCommands extends StatefulEntityCommands
  with CommandsDefining {

  /**
   * The name of user changed.
   *
   * @param id   user id
   * @param name the changed name
   */
  case class NameChanged(id: User.ID, name: Name) extends Notification with ClassNameAsCanonicalName

  object NameChanged extends NotificationDef.Format[NameChanged] with ClassNameAsCanonicalName {implicit val jsonFormat = Json.format[NameChanged]}
}

/**
 * User Actor
 *
 * @author zepeng.li@gmail.com
 */
class UserActor extends StatefulEntityActor
  with UserCanonicalNamed
  with BasicPlayComponents
  with UserNotificationsComponents
  with AkkaTimeOutConfig {

  import UserActor._
  import context.dispatcher

  override def receiveRecover = {
    PartialFunction.empty orElse
      handleEODConfirmed
  } orElse super.receiveRecover

  override def receiveCommand: Receive = ({

    //Get User Information
    case Envelope(_, _: Get) =>
      Future.successful(oneself) pipeTo sender

  }: Receive) orElse
    handleEODConfirm orElse
    super.receiveCommand

  var _users : Users = _
  var oneself: User  = _

  override def preStart() = {
    super.preStart()
    manager ! List(
      User
    )
  }

  def isAllResourcesReady = super.isResourcesReady &&
    _users != null

  override def awaitingResources: Receive = ({

    case List(u: Users) =>
      _users = u
      tryToBecomeResourcesReady()

  }: Receive) orElse super.awaitingResources

  override def initializing: Receive =
    new InitStep[User]("LoadUser") with LastInitStep {
      def loading = PathAs[User.ID].invoke(_users.findBy)
      def onSuccess(t: User) = oneself = t
      def recoverHandler(error: Throwable) = Actor.emptyBehavior
    }.receive
}

trait UserAsDestination extends EntityAsDestination {

  def _userRegion: ActorRef

  def _user(id: User.ID) = _User(id, _userRegion)

  case class _User(id: User.ID, region: ActorRef) extends Destination
}

trait UserCommandsComponents extends UserAsDestination {
  self: AkkaTimeOutConfig =>

  type UserCommandSelector = UserCommands => UserCommands#Command

  implicit class _CommandReceivableUser(_user: _User) {

    def ?(cmd: UserCommandSelector): Future[Any] = {
      _user.region ? Envelope(_user.id, cmd(UserActor))
    }
  }
}

trait UserNotificationsComponents extends UserAsDestination with EOD.Helper {
  self: AtLeastOnceDelivery with ActorLogging =>

  def _userRegion = UserActor.getRegion(context.system)

  implicit class _NotificationReceivableUser(_user: _User) {

    def !?(nti: NotificationDef#Notification): Unit = {
      persist(EOD.Events.NotificationSent(_user.id, nti))(handleEODNotificationSentToUser)
    }
  }

  def handleEODNotificationSentToUser: Receive = {
    case EOD.Events.NotificationSent(id: User.ID, nti) => deliver(_userRegion, id, nti)
  }
}

trait UserRegionComponents extends UserCommandsComponents {
  self: AkkaTimeOutConfig =>

  def actorSystem: ActorSystem

  def _userRegion = UserActor.getRegion(actorSystem)
}