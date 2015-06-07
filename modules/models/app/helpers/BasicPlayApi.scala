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

  def _basicPlayApi: BasicPlayApi

  implicit def messagesApi: MessagesApi = _basicPlayApi.messagesApi

  implicit def langs: Langs = _basicPlayApi.langs

  implicit def configuration: Configuration = _basicPlayApi.configuration

  def applicationLifecycle: ApplicationLifecycle = _basicPlayApi.applicationLifecycle

  def actorSystem: ActorSystem = _basicPlayApi.actorSystem
}

trait DefaultPlayExecutor {
  self: BasicPlayComponents =>

  implicit def defaultContext: ExecutionContext = actorSystem.dispatcher
}