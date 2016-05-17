package services.actors

import java.util.UUID

import akka.actor._
import akka.pattern._
import helpers._

import scala.concurrent._
import scala.reflect._
import scala.util._

/**
 * [[StatefulEntityActor]] 's Companion Object.
 *
 * @author zepeng.li@gmail.com
 */
object StatefulEntityActor extends StatefulEntityCommands

/**
 * Commands definition, which must also be [[Serializable]].
 */
trait CommandsDefining extends Serializable

/**
 * Commands in [[StatefulEntityActor]].
 */
trait StatefulEntityCommands {
  /** Common trait for all commands. */
  trait Command
  /** Get Information of an entity. */
  case object Get extends Command
  /** Get Index of an entity. */
  case object GetIndex extends Command
  /** Message passed among cluster actors, which is not a command. */
  trait Notification
}

/**
 * A Stateful EntityActor
 */
abstract class StatefulEntityActor extends EntityActor
  with BasicPlayComponents
  with I18nLoggingComponents {

  import context.dispatcher

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
   *
   * @param f the function being executed.
   * @tparam T the return type of function f.
   * @return the return value of function f.
   */
  def PathAsUUID[T](f: UUID => Future[T]): Future[T] =
    for {
      _uuid <- Future(UUID.fromString(self.path.name))
      found <- f(_uuid)
    } yield found

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