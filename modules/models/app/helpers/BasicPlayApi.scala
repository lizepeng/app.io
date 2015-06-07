package helpers

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.i18n.{Langs, MessagesApi}
import play.api.inject.ApplicationLifecycle

/**
 * @author zepeng.li@gmail.com
 */
case class BasicPlayApi(
  langs: Langs,
  messagesApi: MessagesApi,
  configuration: Configuration,
  lifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
)

trait BasicPlayComponents {

  def _basicPlayApi: BasicPlayApi

  implicit def messagesApi: MessagesApi = _basicPlayApi.messagesApi

  implicit def langs: Langs = _basicPlayApi.langs

  implicit def configuration: Configuration = _basicPlayApi.configuration

  def lifecycle: ApplicationLifecycle = _basicPlayApi.lifecycle

  def actorSystem: ActorSystem = _basicPlayApi.actorSystem
}