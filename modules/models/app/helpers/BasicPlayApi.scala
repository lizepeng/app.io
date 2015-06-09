package helpers

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.i18n.{Langs, MessagesApi}
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

/**
 * @author zepeng.li@gmail.com
 */
case class BasicPlayApi(
  langs: Langs,
  messagesApi: MessagesApi,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
)

trait BasicPlayComponents {

  def basicPlayApi: BasicPlayApi

  implicit def messagesApi: MessagesApi = basicPlayApi.messagesApi

  implicit def langs: Langs = basicPlayApi.langs

  implicit def configuration: Configuration = basicPlayApi.configuration

  def applicationLifecycle: ApplicationLifecycle = basicPlayApi.applicationLifecycle

  def actorSystem: ActorSystem = basicPlayApi.actorSystem
}

trait DefaultPlayExecutor {
  self: BasicPlayComponents =>

  implicit def defaultContext: ExecutionContext = actorSystem.dispatcher
}