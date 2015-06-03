package helpers

import play.api.Configuration
import play.api.i18n.{Langs, MessagesApi}

/**
 * @author zepeng.li@gmail.com
 */
case class BasicPlayApi(
  langs: Langs,
  messagesApi: MessagesApi,
  configuration: Configuration
)

trait BasicPlayComponents {

  def basicPlayApi: BasicPlayApi

  implicit def messagesApi: MessagesApi = basicPlayApi.messagesApi

  implicit def langs: Langs = basicPlayApi.langs

  implicit def configuration: Configuration = basicPlayApi.configuration
}