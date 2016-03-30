package helpers

import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api._
import play.api.i18n._
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

/**
 * @author zepeng.li@gmail.com
 */
case class BasicPlayApi(
  langs: Langs,
  messagesApi: MessagesApi,
  environment: Environment,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem,
  materializer: Materializer
)

trait BasicPlayComponents {

  def basicPlayApi: BasicPlayApi

  implicit def messagesApi: MessagesApi = basicPlayApi.messagesApi

  implicit def langs: Langs = basicPlayApi.langs

  implicit def environment: Environment = basicPlayApi.environment

  implicit def configuration: Configuration = basicPlayApi.configuration

  implicit def applicationLifecycle: ApplicationLifecycle = basicPlayApi.applicationLifecycle

  implicit def actorSystem: ActorSystem = basicPlayApi.actorSystem

  implicit def materializer: Materializer = basicPlayApi.materializer
}

trait DefaultPlayExecutor {

  def actorSystem: ActorSystem

  implicit def defaultContext: ExecutionContext = actorSystem.dispatcher
}

trait ConfiguredExecutor {

  def actorSystem: ActorSystem

  def lookupExecutionContext(id: String): ExecutionContext = actorSystem.dispatchers.lookup(id)
}