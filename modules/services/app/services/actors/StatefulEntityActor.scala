package services.actors

import akka.actor._
import akka.pattern._
import akka.persistence.AtLeastOnceDelivery._
import akka.persistence._
import helpers._

import scala.concurrent._
import scala.concurrent.duration._
import scala.reflect._
import scala.util._

/**
 * Commands definition, which must also be [[Serializable]].
 */
trait CommandsDefining extends Serializable

/**
 * Commands/Notifications in [[StatefulEntityActor]].
 */
trait StatefulEntityCommands extends CommandDef with NotificationDef {

  /** Get Information of an entity. */
  case class Get() extends Command
  /** Get Index of an entity. */
  case class GetIndex() extends Command
  /** Test Actor */
  case class Test() extends Command
}

/**
 * A Stateful EntityActor
 */
abstract class StatefulEntityActor extends EntityActor
  with AtLeastOnceDelivery
  with BasicPlayComponents
  with I18nLoggingComponents {

  import context.dispatcher

  override def isIdle = numberOfUnconfirmed < 1

  override def receiveRecover: Receive = stateFromSnapshot

  def stateFromSnapshot: Receive = {
    case SnapshotOffer(md, snapshot: AtLeastOnceDeliverySnapshot) => setDeliverySnapshot(snapshot)
  }

  override def handlePrePassivation: Receive = {
    case EntityActor.PrePassivate          => saveSnapshot(stateToSnapshot)
    case SaveSnapshotSuccess(md)           => deleteSnapshots(SnapshotSelectionCriteria(maxTimestamp = md.timestamp - 10.day.toMillis))
    case SaveSnapshotFailure(md, cause)    => log.error(cause, s"Save snapshot failed, $md")
    case DeleteSnapshotsSuccess(cr)        => self ! EntityActor.Passivate
    case DeleteSnapshotsFailure(cr, cause) => self ! EntityActor.Passivate; log.error(cause, s"Delete snapshots failed, $cr")
  }

  def stateToSnapshot: Any = getDeliverySnapshot

  /**
   * After all resources depended are ready, launch the initialization process.
   */
  override def resourcesReady(): Unit = {
    log.debug(s"${self.path.name}, Initializing.")
    self ! InitStep.Start
    context become (initializing orElse handleTimeout orElse stashAll)
  }

  /**
   * Initialization process.
   */
  def initializing: Receive = initializeNothing

  /**
   * Initialization process that initialize nothing.
   */
  def initializeNothing: Receive = {
    case InitStep.Start => super.resourcesReady()
  }

  /**
   * Convert {{{self.path.name}}} to UUID, then invoke another function with it.
   */
  def PathAs[ID](implicit sf: Stringifier[ID]) = new EntityFinder[ID](sf << self.path.name)

  /**
   * Invoke another function to find entity.
   *
   * @param id the id which will be used to find entity.
   * @tparam ID the type of id.
   */
  class EntityFinder[ID](id: Try[ID]) {

    def invoke[T](f: ID => Future[T]): Future[T] = Future.fromTry(id).flatMap(f)
  }

  /**
   * A Step of initialization process which will never be recovered.
   */
  trait InitStepNeverRecover {
    self: InitStep[_] =>

    def recoverHandler(e: Throwable): Receive = Actor.emptyBehavior
  }

  /**
   * A Step of initialization process which is the last one.
   */
  trait LastInitStep {
    self: InitStep[_] =>

    def nextStepOption: Option[InitStep[_]] = None
  }

  /**
   * A Step of initialization process during actor starting.
   *
   * @param name the name of the step.
   * @tparam T the type of the step '''caution！: plain type such as Boolean does not work.'''
   */
  abstract class InitStep[T: ClassTag](name: String) {

    /**
     * The real part of this step.
     *
     * @return e.g. return some data from DB
     */
    def loading: Future[T]

    /**
     * The success callback function of [[loading]], for initializing internal fields reside in Actor.
     *
     * @param t e.g. the type of data loaded from DB
     */
    def onSuccess(t: T): Unit

    /**
     * The failure callback function of [[loading]], for recovering actor to some other [[Receive]].
     *
     * @param error the Exception thrown in [[loading]]
     * @return recovered wrong status to some [[Receive]]
     */
    def recoverHandler(error: Throwable): Receive

    /**
     * The next [[InitStep]] of current step.
     *
     * @return return None if there is no next step
     */
    def nextStepOption: Option[InitStep[_]]

    /**
     * Actor behavior
     */
    def receive: Receive = {

      case InitStep.Start =>
        if (sender == self) {
          log.debug(s"${self.path.name}, InitStep[$name] started.")
          loading.onComplete(self ! _)
        }

      //Loading succeeded.
      case Success(o) if classTag[T].runtimeClass.isInstance(o) =>
        onSuccess(o.asInstanceOf[T])
        self ! InitStep.GoToNext

      case InitStep.GoToNext =>
        log.debug(s"${self.path.name}, InitStep[$name] completed.")
        self ! InitStep.Complete

      //Loading failed - BaseException
      case Failure(e: BaseException) =>
        log.debug(s"${self.path.name}, InitStep[$name] stopped. Reason : ${e.reason}")
        becomeReceiveReady(
          recoverHandler(e)
            orElse handleRecovered
            orElse handleTimeout
            orElse { case _ if sender != self => Future.failed(e) pipeTo sender }
        )

      //Loading failed - Not a BaseException
      case Failure(e: Throwable) =>
        log.debug(s"${self.path.name}, Initialization failed. Reason : ${e.getMessage}")
        becomeReceiveReady(
          Actor.emptyBehavior
            orElse handleTimeout
            orElse { case _ if sender != self => Future.failed(e) pipeTo sender }
        )

      case InitStep.Complete =>
        nextStepOption match {
          case Some(nextStep) =>
            self ! InitStep.Start
            context become (nextStep.receive orElse handleTimeout)
          case None           =>
            log.debug(s"${self.path.name}, Initialization successful.")
            onInitializationSuccessfulBecome()
        }
    }

    def handleRecovered: Receive = {

      case InitStep.Recovered(t: T) =>
        log.debug(s"${self.path.name}, InitStep[$name] recovered.")
        onSuccess(t)
        self ! InitStep.Complete
        context become initializing
    }
  }

  protected object InitStep {
    /** Internal Command - Launch an InitStep. */
    case object Start
    /** Internal Command - Go to next InitStep. */
    case object GoToNext
    /** Internal Command - InitStep is recovered from a wrong status. */
    case class Recovered[T](t: T)
    /** Internal Command - Complete an InitStep. */
    case object Complete
  }

  /**
   * After initializing the actor successfully, jump to a [[Receive]], the default one is [[receive]]
   */
  def onInitializationSuccessfulBecome(): Unit = becomeReceiveReady(receive)
}